## The Sinai Scholars Website

### To build locally (without Vagrant)

Check the project out from GitHub, package it and start it.

    git clone https://github.com/UCLALibrary/sinai-web
    cd sinai-web
    mvn clean package
    target/startup.sh --env <dev|test|stage|prod>

The `--env` argument allows for selection of the Solr core and IIIF image server to connect to:

|`--env`|Solr server|IIIF image server|
|---|---|---|
|`dev`|`sinai.solr.server` according to `pom.xml`|https://sinai-images.library.ucla.edu|
|`test`|http://test-solr.library.ucla.edu/solr/sinaimeta|https://test-sinai-images.library.ucla.edu|
|`stage`|http://solr.library.ucla.edu/solr/sinaistagemeta|https://stage-sinai-images.library.ucla.edu|
|`prod`|http://solr.library.ucla.edu/solr/sinaimeta|https://sinai-images.library.ucla.edu|

It generates a self-signed certificate, so when you visit

https://localhost:8443/

You will have to click through the security warning.

### To build locally (using Ansible and Vagrant)

Check out the Library's restricted access 'ansible' project and run:

    git clone https://github.com/UCLALibrary/ansible
    cd ansible
    PLAYBOOK=sinai_scholars_stage vagrant up

This also generates a self-signed certificate, so when you visit

https://localhost/

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
    
### Propagating Changes

The current workflow for propagating code changes is as follows:
* All changes start out on branch `develop`. These changes may be directly committed on `develop`, or merged in from another branch via pull request. These changes should be well-formed, using `git rebase` on your local copy to squash commits as necessary.
* Both `stage` and `master` should only be updated with a pull request, rather than being pushed to directly. The convention is to create a new branch for the pull request that is deleted after the pull requst is complete. For example, one would create a `stage-pr` branch for a pull request on `stage`, a `master-pr` branch for a pull request on `master`, etc. The procedure is as follows:

        git checkout stage
        git pull
        git checkout -b stage-pr
        git merge develop
        git push origin stage-pr
        
        # submit PR and wait for completion
        
        git branch -D stage-pr
        git push --delete origin stage-pr
        
* **NOTE**: so that `master` is not polluted with merge commits, all `{name}-pr` branches must contain new commits from `develop` only (i.e., when preparing `master-pr`, merge in `develop`, not `stage`)
* **NOTE**: so that whenever `master` is updated it becomes identical to `stage`, the PR branches for each are always to be prepared at the same time

        git checkout master
        git pull
        git checkout -b master-pr
        git merge develop
        git push origin master-pr

        # submit PR and wait for completion

        git branch -D master-pr
        git push --delete origin master-pr

### Using a DDNS Domain with the startup.sh Script

If you want to login with the new EMEL auth system and a DDNS service, you can supply the Sinai host variable at the point of running the test startup script; for instance:

    SINAI_HOST=lisforge.ddns.net target/startup.sh

This is probably only relevant to developers testing the system using an allowed domain name. When supplying the host in this fashion, it's assumed the service is running at port 443 rather than at the out of the box testing default, 8443.

### Contact

Contact Kevin or Mark with any build, etc., questions you have.
