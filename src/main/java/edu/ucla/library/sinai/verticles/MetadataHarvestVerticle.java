
package edu.ucla.library.sinai.verticles;

import static edu.ucla.library.sinai.Constants.MESSAGES;

import java.io.IOException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;

import edu.ucla.library.sinai.Configuration;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A verticle that periodically queries the KatIkon PostgreSQL database and
 * updates our Solr index of the metadata.
 */
public class MetadataHarvestVerticle extends AbstractSinaiVerticle {

    /**
     * Handler that contains the logic for updating Solr with metadata records from the database.
     */
    static class MetadataHarvestHandler implements Handler<Long> {

        /**
         * Data structure containing information for mapping database columns to Solr fields.
         * - name: name of the destination Solr field
         * - alias: qualified name (<table>.<column>) of the source database column (empty if "name" can identify the column)
         * - type: type of the destination Solr field (must be either "string" or "int")
         * - multiValued: whether field is multi-valued or not
         */
        class MetadataHarvestDBFields {
            public String name, alias, type;
            public Boolean multiValued;

            public MetadataHarvestDBFields(String aName, String aAlias, String aType, Boolean isMultiValued) {
                name = aName;
                alias = aAlias;
                type = aType;
                multiValued = isMultiValued;
            }
        }

        protected final Logger LOGGER = LoggerFactory.getLogger(getClass(), MESSAGES);

        private final Properties myDatabaseProps;
        private final String myDatabaseUrl;
        private final SolrServer mySolrServer;

        public MetadataHarvestHandler(final Configuration aConfig) {

            final JsonObject databaseProperties = aConfig.getPostgreSQLProperties();

            // Configure PostgreSQL
            myDatabaseUrl = "jdbc:postgresql://"
                    + databaseProperties.getString("host") + ":"
                    + databaseProperties.getString("port") + "/"
                    + databaseProperties.getString("database");
            myDatabaseProps = new Properties();
            myDatabaseProps.setProperty("user", databaseProperties.getString("user"));
            myDatabaseProps.setProperty("password", databaseProperties.getString("password"));
            myDatabaseProps.setProperty("ssl", databaseProperties.getString("ssl"));
            // FIXME: eventually want to use a stronger SSL configuration
            myDatabaseProps.setProperty("sslfactory", databaseProperties.getString("sslfactory"));

            mySolrServer = aConfig.getSolrServer();
        }

        private String solrDynamicFieldSuffix(String type, Boolean multiValued) {
            String ret = "";
            if (type.equals("int")) {
                ret = "_i";
            } else if (type.equals("string")) {
                ret = "_s";
            } else if (type.equals("boolean")) {
                ret = "_b";
            } else if (type.equals("date")) {
                ret = "_dt";
            } else {
                final String errorMessage = "invalid Solr field type: we aren't using \"" + type + "\" for Sinai!";
                LOGGER.error(errorMessage);
                throw new Error(errorMessage);
            }

            if (multiValued == true) {
                ret += "s";
            }
            return ret;
        }

        /**
         * Updates our Solr index.
         *
         * @param doctype {String} Either "manuscript" or "undertext_object"
         * @param fields {MetadataHarvestDBFields[]} Array
         * @param sql {String} SQL select query for grabbing the records we want to Solr-ize
         * @param conn {Connection} Database connection object
         */
        private void updateSolr(final String doctype, final MetadataHarvestDBFields[] fields, final String sql, final Connection conn, final String multiValuedFieldDelimiter) {
            SolrInputDocument doc;
            final String errorMessage;

            String solrFieldName;
            Object solrFieldValue;
            Map<String, Object> solrInputField;

            try (
                final Statement st = conn.createStatement();
                final ResultSet rs = st.executeQuery(sql)
            ) {
                while (rs.next()) {
                    doc = new SolrInputDocument();
                    doc.addField("record_type_s", doctype);
                    for (int i = 0; i < fields.length; i++) {

                        if (fields[i].type.equals("string")) {
                            final String strVal;
                            final Array sqlArrayVal;
                            final String[] arrayVal;

                            if (fields[i].multiValued) {
                                sqlArrayVal = rs.getArray(fields[i].name);
                                if (sqlArrayVal != null) {
                                    arrayVal = (String[])sqlArrayVal.getArray();
                                } else {
                                    continue;
                                }

                                // Only put in Solr if not null and not empty
                                if (rs.wasNull() == false && arrayVal != null && arrayVal.length > 0) {
                                    solrFieldValue = Arrays.asList(arrayVal);

                                } else {
                                    continue;
                                }
                            } else {
                                strVal = rs.getString(fields[i].name);

                                // Only put in Solr if not null and not empty
                                if (rs.wasNull() == false && strVal != null && !strVal.equals("")) {
                                    solrFieldValue = strVal;
                                } else {
                                    continue;
                                }
                            }
                        } else if (fields[i].type.equals("int")) {
                            final Integer intVal = rs.getInt(fields[i].name);

                            if (rs.wasNull() == false) {
                                // Disallow representing multi-valued fields with ints
                                if (fields[i].multiValued) {
                                    errorMessage = "Solr multiValued field must only be derived from strings";
                                    LOGGER.error(errorMessage);
                                    throw new Error(errorMessage);
                                } else {
                                    solrFieldValue = intVal;
                                }
                            } else {
                                continue;
                            }
                        } else if (fields[i].type.equals("boolean")) {
                            final boolean boolVal = rs.getBoolean(fields[i].name);

                            if (rs.wasNull() == false) {
                                // Disallow representing multi-valued fields with ints
                                if (fields[i].multiValued) {
                                    errorMessage = "Solr multiValued field must only be derived from strings";
                                    LOGGER.error(errorMessage);
                                    throw new Error(errorMessage);
                                } else {
                                    solrFieldValue = boolVal;
                                }
                            } else {
                                continue;
                            }
                        } else {
                            errorMessage = "Solr field type must be either string, int, or boolean";
                            LOGGER.error(errorMessage);
                            throw new Error(errorMessage);
                        }

                        if (fields[i].name.equals("id")) {
                            solrFieldName = fields[i].name;
                            doc.addField(solrFieldName, solrFieldValue);
                        } else {
                            solrFieldName = fields[i].name + solrDynamicFieldSuffix(fields[i].type, fields[i].multiValued);
                            // http://yonik.com/solr/atomic-updates/
                            solrInputField = new HashMap<String, Object>(1);
                            solrInputField.put("set", solrFieldValue);
                            doc.addField(solrFieldName, solrInputField);
                        }
                    }

                    LOGGER.debug(doc.toString());
                    mySolrServer.add(doc);
                }
                rs.close();
                st.close();

                mySolrServer.commit();

            } catch (IOException e) {
                LOGGER.error("Cannot write to Solr: " + e.getMessage());
            } catch (SolrServerException e) {
                LOGGER.error("Cannot write to Solr: " + e.getMessage());
            } catch (SQLException e) {
                LOGGER.error("Database error: " + e.getMessage());
            } catch (Exception e) {
                LOGGER.error("Error: " + e.getMessage());
            }
        }

        @Override
        public void handle(Long arg0) {
            LOGGER.debug("Starting metadata harvest");

            try (final Connection conn = DriverManager.getConnection(myDatabaseUrl, myDatabaseProps)) {
                // Add manuscripts
                final String multiValuedFieldDelimiter = ",";

                final MetadataHarvestDBFields[] manuscriptsFields = {
                    new MetadataHarvestDBFields("id", "m.uuid", "string", false),
                    new MetadataHarvestDBFields("manuscript_id", "m.id", "int", false),
                    new MetadataHarvestDBFields("shelf_mark", "m.shelf_mark", "string", false),
                    new MetadataHarvestDBFields("title", "m.title", "string", false),
                    new MetadataHarvestDBFields("primary_language", "m.primary_language", "string", false),
                    new MetadataHarvestDBFields("secondary_languages", "array_remove(array_replace(ARRAY[m.secondary_language_1, m.secondary_language_2, m.secondary_language_3], '', NULL), NULL)", "string", true),
                    new MetadataHarvestDBFields("language_description", "m.language_description", "string", false),
                    new MetadataHarvestDBFields("script", "m.script", "string", false),
                    new MetadataHarvestDBFields("script_note", "m.script_note", "string", false),
                    new MetadataHarvestDBFields("date_text", "m.date_text", "string", false),
                    new MetadataHarvestDBFields("date_of_origin_start", "m.date_of_origin_start", "int", false),
                    new MetadataHarvestDBFields("date_of_origin_end", "m.date_of_origin_end", "int", false),
                    new MetadataHarvestDBFields("place_of_origin", "m.place_of_origin", "string", false),
                    new MetadataHarvestDBFields("community_of_origin", "m.community_of_origin", "string", false),
                    new MetadataHarvestDBFields("decoration_note", "m.decoration_note", "string", false),
                    new MetadataHarvestDBFields("support_material", "m.support_material", "string", false),
                    new MetadataHarvestDBFields("folio_count", "m.folio_count", "int", false),
                    new MetadataHarvestDBFields("current_form", "mt.name", "string", false),
                    new MetadataHarvestDBFields("manuscript_condition", "m.manuscript_condition", "string", false),
                    new MetadataHarvestDBFields("manuscript_height", "m.manuscript_height", "int", false),
                    new MetadataHarvestDBFields("manuscript_width", "m.manuscript_width", "int", false),
                    new MetadataHarvestDBFields("manuscript_depth", "m.manuscript_depth", "int", false),
                    new MetadataHarvestDBFields("folio_height", "m.folio_height", "int", false),
                    new MetadataHarvestDBFields("folio_width", "m.folio_width", "int", false),
                    new MetadataHarvestDBFields("binding_status", "m.binding_status", "string", false),
                    new MetadataHarvestDBFields("binding_description", "m.binding_description", "string", false),
                    new MetadataHarvestDBFields("binding_condition", "m.binding_condition", "string", false),
                    new MetadataHarvestDBFields("quire_structure", "m.quire_structure", "string", false),
                    new MetadataHarvestDBFields("foliation_note", "m.foliation_note", "string", false),
                    new MetadataHarvestDBFields("codicological_note", "m.codicological_note", "string", false),
                    new MetadataHarvestDBFields("previous_catalog_information", "m.previous_catalog_information", "string", false)
                };
                final String manuscriptsSql = "SELECT " + String.join(",", Arrays.stream(manuscriptsFields).map(s -> {
                    return s.alias + " AS " + s.name;
                }).toArray(String[]::new)) + " FROM manuscripts AS m INNER JOIN manuscript_types AS mt ON m.manuscript_type_id = mt.id";
                updateSolr("manuscript", manuscriptsFields, manuscriptsSql, conn, multiValuedFieldDelimiter);

                // Add UTOs
                final MetadataHarvestDBFields[] utoFields = {
                    new MetadataHarvestDBFields("id", "uto.uuid", "string", false),
                    new MetadataHarvestDBFields("undertext_object_id", "uto.id", "int", false),
                    new MetadataHarvestDBFields("manuscript_id", "tlg.manuscript_id", "int", false),
                    new MetadataHarvestDBFields("shelf_mark", "m.shelf_mark", "string", false),
                    new MetadataHarvestDBFields("author", "uto.author", "string", false),
                    new MetadataHarvestDBFields("work", "uto.work", "string", false),
                    new MetadataHarvestDBFields("genre", "uto.genre", "string", false),
                    new MetadataHarvestDBFields("primary_language", "uto.primary_language", "string", false),
                    new MetadataHarvestDBFields("script_name", "uto.script_name", "string", false),
                    new MetadataHarvestDBFields("script_characterization", "uto.script_characterization", "string", false),
                    new MetadataHarvestDBFields("secondary_languages", "array_remove(array_replace(ARRAY[uto.secondary_language_1, uto.secondary_language_2, uto.secondary_language_3], '', NULL), NULL)", "string", true),
                    new MetadataHarvestDBFields("script_date_text", "uto.script_date_text", "string", false),
                    new MetadataHarvestDBFields("script_date_start", "uto.script_date_start", "int", false),
                    new MetadataHarvestDBFields("script_date_end", "uto.script_date_end", "int", false),
                    new MetadataHarvestDBFields("place_of_origin", "uto.place_of_origin", "string", false),
                    new MetadataHarvestDBFields("layout_comments", "uto.layout_comments", "string", false),
                    new MetadataHarvestDBFields("folios", "g.folios", "string", true),
                    new MetadataHarvestDBFields("undertext_folio_order", "uto.undertext_folio_order", "string", false),
                    new MetadataHarvestDBFields("folio_order_comments", "uto.folio_order_comments", "string", false),
                    new MetadataHarvestDBFields("related_undertext_objects", "uto.related_undertext_objects", "string", false),
                    new MetadataHarvestDBFields("text_remarks", "uto.text_remarks", "string", false),
                    new MetadataHarvestDBFields("bibliography", "uto.bibliography", "string", false),
                    new MetadataHarvestDBFields("scholar_name", "x.scholar_name", "string", true)
                };
                final String utoSql = "SELECT " + String.join(",", Arrays.stream(utoFields).map(s -> {
                    return s.alias + " AS " + s.name;
                }).toArray(String[]::new)) + " FROM undertext_objects AS uto INNER JOIN text_layer_groupings AS tlg ON uto.text_layer_grouping_id = tlg.id INNER JOIN manuscripts AS m ON tlg.manuscript_id = m.id LEFT OUTER JOIN ( SELECT f.undertext_object_id, ARRAY_AGG( f.folio_number || f.folio_side ) AS folios FROM ( SELECT tl.undertext_object_id, mc.folio_number, mc.folio_side, mc.position AS pos FROM text_layers AS tl INNER JOIN manuscript_components AS mc ON tl.manuscript_component_id = mc.id ORDER BY pos ) AS f GROUP BY undertext_object_id) AS g ON g.undertext_object_id = uto.id LEFT OUTER JOIN( SELECT y.text_layer_grouping_id, ARRAY_AGG( y.last_name ) AS scholar_name FROM ( SELECT ga.text_layer_grouping_id, u.last_name FROM grouping_assignments AS ga INNER JOIN users AS u ON ga.scholar_id = u.id ORDER BY u.last_name) AS y GROUP BY text_layer_grouping_id ) AS x ON tlg.id = x.text_layer_grouping_id";
                updateSolr("undertext_object", utoFields, utoSql, conn, multiValuedFieldDelimiter);

                // Add folios (manuscript components)
                final MetadataHarvestDBFields[] folioFields = {
                    new MetadataHarvestDBFields("id", "mc.uuid", "string", false),
                    new MetadataHarvestDBFields("manuscript_id", "mc.manuscript_id", "int", false),
                    new MetadataHarvestDBFields("manuscript_component_id", "mc.id", "int", false),

                    // Used to sort
                    new MetadataHarvestDBFields("position", "mc.position", "int", false),
                    new MetadataHarvestDBFields("component_type", "mc.component_type", "string", false),
                    new MetadataHarvestDBFields("folio_number", "mc.folio_number", "string", false),
                    new MetadataHarvestDBFields("folio_side", "mc.folio_side", "string", false),

                    new MetadataHarvestDBFields("leading_conjoin_component_type", "leading_conjoins.leading_conjoin_component_type", "string", false),
                    new MetadataHarvestDBFields("leading_conjoin_folio_number", "leading_conjoins.leading_conjoin_folio_number", "string", false),
                    new MetadataHarvestDBFields("leading_conjoin_folio_side", "leading_conjoins.leading_conjoin_folio_side", "string", false),

                    new MetadataHarvestDBFields("trailing_conjoin_component_type", "trailing_conjoins.trailing_conjoin_component_type", "string", false),
                    new MetadataHarvestDBFields("trailing_conjoin_folio_number", "trailing_conjoins.trailing_conjoin_folio_number", "string", false),
                    new MetadataHarvestDBFields("trailing_conjoin_folio_side", "trailing_conjoins.trailing_conjoin_folio_side", "string", false),

                    new MetadataHarvestDBFields("quire", "mc.quire", "string", false),
                    new MetadataHarvestDBFields("quire_position", "mc.quire_position", "string", false),
                    new MetadataHarvestDBFields("alternate_numbering", "mc.alternate_numbering", "string", false),

                    new MetadataHarvestDBFields("folio_dimensions", "mc.folio_dimensions", "string", false),
                    new MetadataHarvestDBFields("max_height", "mc.max_height", "int", false),
                    new MetadataHarvestDBFields("max_width", "mc.max_width", "int", false),
                    new MetadataHarvestDBFields("min_height", "mc.min_height", "int", false),
                    new MetadataHarvestDBFields("min_width", "mc.min_width", "int", false),
                    new MetadataHarvestDBFields("flesh_hair_side", "mc.flesh_hair_side", "string", false),
                    new MetadataHarvestDBFields("parchment_quality", "mc.parchment_quality", "string", false),
                    new MetadataHarvestDBFields("parchment_description", "mc.parchment_description", "string", false),
                    new MetadataHarvestDBFields("palimpsested", "mc.palimpsested", "string", false),
                    new MetadataHarvestDBFields("erasure_method", "mc.erasure_method", "string", false),

                    // for Overtext layer description
                    new MetadataHarvestDBFields("decoration", "mc.decoration", "string", false),
                };
                final String folioSql = "SELECT " + String.join(",", Arrays.stream(folioFields).map(s -> {
                    return s.alias + " AS " + s.name;
                }).toArray(String[]::new)) + " FROM manuscript_components AS mc LEFT OUTER JOIN ( SELECT id, component_type AS leading_conjoin_component_type, folio_number AS leading_conjoin_folio_number, folio_side AS leading_conjoin_folio_side FROM manuscript_components ) AS leading_conjoins ON mc.leading_conjoin_id = leading_conjoins.id LEFT OUTER JOIN ( SELECT leading_conjoin_id AS trailing_conjoin_id, component_type AS trailing_conjoin_component_type, folio_number AS trailing_conjoin_folio_number, folio_side AS trailing_conjoin_folio_side FROM manuscript_components ) AS trailing_conjoins ON mc.id = trailing_conjoins.trailing_conjoin_id";
                updateSolr("manuscript_component", folioFields, folioSql, conn, multiValuedFieldDelimiter);

                // Add under text layers
                final MetadataHarvestDBFields[] underTextLayerFields = {
                    new MetadataHarvestDBFields("id", "tl.uuid", "string", false),
                    new MetadataHarvestDBFields("manuscript_id", "mc.manuscript_id", "int", false),
                    new MetadataHarvestDBFields("undertext_object_id", "tl.undertext_object_id", "int", false),
                    new MetadataHarvestDBFields("manuscript_component_id", "tl.manuscript_component_id", "int", false),
                    new MetadataHarvestDBFields("work_passage", "tl.work_passage", "string", false),
                    new MetadataHarvestDBFields("primary_language", "tl.primary_language", "string", false),
                    new MetadataHarvestDBFields("script_note", "tl.script_note", "string", false),
                    new MetadataHarvestDBFields("secondary_languages", "array_remove(array_replace(ARRAY[tl.secondary_language_1, tl.secondary_language_2, tl.secondary_language_3], '', NULL), NULL)", "string", true),
                    new MetadataHarvestDBFields("marginalia_present", "tl.marginalia_present", "boolean", false),
                    new MetadataHarvestDBFields("marginalia", "tl.marginalia", "string", false),
                    new MetadataHarvestDBFields("nontextual_content_present", "tl.nontextual_content_present", "boolean", false),
                    new MetadataHarvestDBFields("nontextual_content", "tl.nontextual_content", "string", false),
                    new MetadataHarvestDBFields("catchwords", "tl.catchwords", "string", false),
                    new MetadataHarvestDBFields("signatures", "tl.signatures", "string", false),
                    new MetadataHarvestDBFields("under_text_orientation", "tl.under_text_orientation", "int", false),
                    new MetadataHarvestDBFields("legibility", "tl.legibility", "int", false),
                    new MetadataHarvestDBFields("prickings", "tl.prickings", "boolean", false),
                    new MetadataHarvestDBFields("ruled_lines", "tl.ruled_lines", "boolean", false),
                    new MetadataHarvestDBFields("preservation_notes", "tl.preservation_notes", "string", false),
                    new MetadataHarvestDBFields("remarks", "tl.remarks", "string", false),
                    new MetadataHarvestDBFields("notes", "tl.notes", "string", false)
                };
                final String underTextLayerSql = "SELECT " + String.join(",", Arrays.stream(underTextLayerFields).map(s -> {
                    return s.alias + " AS " + s.name;
                }).toArray(String[]::new)) + " FROM text_layers AS tl INNER JOIN manuscript_components AS mc ON tl.manuscript_component_id = mc.id WHERE tl.type = 'UnderTextLayer' AND tl.undertext_object_id IS NOT NULL";
                LOGGER.info(underTextLayerSql);
                updateSolr("undertext_layer", underTextLayerFields, underTextLayerSql, conn, multiValuedFieldDelimiter);

                // Add over text layers
                final MetadataHarvestDBFields[] overTextLayerFields = {
                    new MetadataHarvestDBFields("id", "tl.uuid", "string", false),
                    new MetadataHarvestDBFields("manuscript_id", "mc.manuscript_id", "int", false),
                    new MetadataHarvestDBFields("manuscript_component_id", "tl.manuscript_component_id", "int", false),
                    new MetadataHarvestDBFields("text_identity", "tl.text_identity", "string", false),
                    new MetadataHarvestDBFields("primary_language", "tl.primary_language", "string", false),
                    new MetadataHarvestDBFields("script", "tl.script", "string", false),
                    new MetadataHarvestDBFields("script_note", "tl.script_note", "string", false),
                    new MetadataHarvestDBFields("script_date_text", "tl.script_date_text", "string", false),
                    new MetadataHarvestDBFields("script_date_start", "tl.script_date_start", "int", false),
                    new MetadataHarvestDBFields("script_date_end", "tl.script_date_end", "int", false),
                    new MetadataHarvestDBFields("marginalia_present", "tl.marginalia_present", "boolean", false),
                    new MetadataHarvestDBFields("marginalia", "tl.marginalia", "string", false),
                    new MetadataHarvestDBFields("nontextual_content_present", "tl.nontextual_content_present", "boolean", false),
                    new MetadataHarvestDBFields("nontextual_content", "tl.nontextual_content", "string", false),
                    // from manuscript_components: decoration
                    new MetadataHarvestDBFields("notes", "tl.notes", "string", false)
                };
                final String overTextLayerSql = "SELECT " + String.join(",", Arrays.stream(overTextLayerFields).map(s -> {
                    return s.alias + " AS " + s.name;
                }).toArray(String[]::new)) + " FROM text_layers AS tl INNER JOIN manuscript_components AS mc ON tl.manuscript_component_id = mc.id WHERE tl.type = 'OverTextLayer'";
                updateSolr("overtext_layer", overTextLayerFields, overTextLayerSql, conn, multiValuedFieldDelimiter);

                LOGGER.debug("Metadata harvest completed");

                conn.close();
            } catch (final SQLException e){
                LOGGER.error("Unable to connect to database " + myDatabaseUrl + " - " + e.getMessage());
            }
        }
    }

    private long myTimerId;

    @Override
    public void start(final Future<Void> aFuture) throws Exception {
        final Configuration config = getConfiguration();
        final long metadataHarvestInterval = config.getMedatadaHarvestInterval();
        final ZonedDateTime initialHarvestTime = ZonedDateTime.now().plus(metadataHarvestInterval, ChronoUnit.MILLIS);

        myTimerId = vertx.setPeriodic(metadataHarvestInterval, new MetadataHarvestHandler(config));

        LOGGER.debug("Initial metadata harvest will be run at approximately {}", initialHarvestTime);

        aFuture.complete();
    }

    @Override
    public void stop() {
        // tear down timer
        vertx.cancelTimer(myTimerId);
    }

    // TODO: need a method to listen for manual triggers of re-harvest
}
