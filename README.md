## The Sinai Scholars Website

### To build locally

Check the project out from GitHub, package it and start it.

    git clone https://github.com/UCLALibrary/sinai-web
    cd sinai-web
    mvn clean package
    target/startup.sh  # Note that running this doesn't work unless you have permission to access the Solr server

Since there are no more test/stage servers associated with this project, the following production instances of the IIIF image server and Solr search engine will be used (even when running locally):

* Solr - http://solr.library.ucla.edu/solr/sinaimeta (not publicly accessible)
* IIIF image server - https://sinai-images.library.ucla.edu

It generates a self-signed certificate, so when you visit

https://localhost:8443/

You will have to click through the security warning.

### Connecting a JDWP agent or JMX monitor

You can build the project with support for connecting a JDWP agent by running with:

    mvn clean install -Ddev.tools=JDWP_AGENTLIB

Or with support for a JMX monitor:

    mvn clean install -Ddev.tools=JMX_REMOTE

Or with both:

    mvn clean install -Ddev.tools="JDWP_AGENTLIB JMX_REMOTE"

You can also supply the `dev.tools` variable in a default Maven profile. See `src/main/resources/settings.xml` for an example.

### Updating Mirador

The Sinai Scholar's site uses [Mirador](http://projectmirador.org/) as its image viewer. We are maintaining a fork [here](https://github.com/UCLALibrary/mirador). The build output of the code on the `develop-prod` branch of that repository is included in this repository (`sinai-web`) by the following process:

    SINAI_PATH=/path/to/sinai-web
    SINAI_MIRADOR_PATH=${SINAI_PATH}/src/main/webapp/mirador
    
    git clone https://github.com/UCLALibrary/mirador
    cd mirador
    git checkout -b develop-prod origin/develop-prod
    grunt && grunt uglify
    
    rm -rf ${SINAI_MIRADOR_PATH}
    cp -r ./mirador ${SINAI_MIRADOR_PATH}

### Contact

Contact Kevin S. Clarke with any build, etc., questions you have.
