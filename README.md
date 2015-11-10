## The skeleton for the Sinai Scholars Website

### To build locally (without Vagrant)

Check the project out from GitHub, package it and start it.

    git clone https://github.com/UCLALibrary/sinai-web
    cd sinai-web
    mvn clean package
    target/startup.sh

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

### Contact

Contact Kevin with any build, etc., questions you have.
