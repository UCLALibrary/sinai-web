
package edu.ucla.library.sinai.verticles;

import static edu.ucla.library.sinai.Constants.MESSAGES;

import java.io.IOException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

        public MetadataHarvestHandler() {

            final JsonObject databaseProperties = myConfig.getPostgreSQLProperties();

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

            mySolrServer = myConfig.getSolrServer();
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
                    doc.addField("record_type", doctype);
                    for (int i = 0; i < fields.length; i++) {

                        if (fields[i].name.equals("uuid") || fields[i].name.equals("id") || fields[i].name.equals("manuscript_id")) {
                            solrFieldName = fields[i].name;
                        } else {

                            if (fields[i].type.equals("string")) {
                                solrFieldName = fields[i].name + "_s";
                            } else if (fields[i].type.equals("int")) {
                                solrFieldName = fields[i].name + "_i";
                            } else {
                                errorMessage = "Solr field type must be either string or int";
                                LOGGER.error(errorMessage);
                                throw new Error(errorMessage);
                            }
                        }

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
                        } else {
                            errorMessage = "Solr field type must be either string or int";
                            LOGGER.error(errorMessage);
                            throw new Error(errorMessage);
                        }

                        if (solrFieldName.equals("uuid")) {
                            doc.addField(solrFieldName, solrFieldValue);
                        } else {
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
            try (final Connection conn = DriverManager.getConnection(myDatabaseUrl, myDatabaseProps)) {
                // Add manuscripts
                final String multiValuedFieldDelimiter = ",";

                final MetadataHarvestDBFields[] manuscriptsFields = {
                    new MetadataHarvestDBFields("uuid", "m.uuid", "string", false),
                    new MetadataHarvestDBFields("id", "m.id", "int", false),
                    new MetadataHarvestDBFields("shelf_mark", "m.shelf_mark", "string", false),
                    new MetadataHarvestDBFields("title", "m.title", "string", false),
                    new MetadataHarvestDBFields("primary_language", "m.primary_language", "string", false),
                    new MetadataHarvestDBFields("secondary_language", "array_remove(array_replace(ARRAY[m.secondary_language_1, m.secondary_language_2, m.secondary_language_3], '', NULL), NULL)", "string", true),
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
                    new MetadataHarvestDBFields("uuid", "uto.uuid", "string", false),
                    new MetadataHarvestDBFields("manuscript_id", "tlg.manuscript_id", "int", false),
                    new MetadataHarvestDBFields("shelf_mark", "m.shelf_mark", "string", false),
                    new MetadataHarvestDBFields("author", "uto.author", "string", false),
                    new MetadataHarvestDBFields("work", "uto.work", "string", false),
                    new MetadataHarvestDBFields("genre", "uto.genre", "string", false),
                    new MetadataHarvestDBFields("primary_language", "uto.primary_language", "string", false),
                    new MetadataHarvestDBFields("script_name", "uto.script_name", "string", false),
                    new MetadataHarvestDBFields("script_characterization", "uto.script_characterization", "string", false),
                    new MetadataHarvestDBFields("secondary_language", "array_remove(array_replace(ARRAY[uto.secondary_language_1, uto.secondary_language_2, uto.secondary_language_3], '', NULL), NULL)", "string", true),
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

                conn.close();
            } catch (SQLException e){
                LOGGER.error("Unable to connect to database " + myDatabaseUrl + " - " + e.getMessage());
            }
        }
    }

    private long myTimerId;
    private static Configuration myConfig;

    @Override
    public void start(final Future<Void> aFuture) throws Exception {
        myConfig = getConfiguration();

        MetadataHarvestHandler handler = new MetadataHarvestHandler();

        // set period to once per day
        final Integer period = 1000 * 60 * 60 * 24;
        myTimerId = vertx.setPeriodic(period, handler);

        aFuture.complete();
    }

    @Override
    public void stop() {
        // tear down timer
        vertx.cancelTimer(myTimerId);
    }

    // TODO: need a method to listen for manual triggers of re-harvest
}
