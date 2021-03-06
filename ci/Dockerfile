FROM ubuntu:bionic

RUN apt-get update \
    && apt-get install -y \
    openjdk-8-jdk \
    git \
    python2.7 \
    python-setuptools \
    python-six \
    python-yaml \
    sudo \
    maven \
    apt-transport-https \
    ca-certificates \
    curl \
    software-properties-common

RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -

RUN add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu bionic stable" \
    && apt-get update \
    && apt-cache policy docker-ce \
    && apt-get install docker-ce -y

RUN update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java && \
    export JAVA_HOME="/usr/lib/jvm/java-1.8.0-openjdk-amd64" && \
    export PATH=$PATH:$JAVA_HOME/bin && \
    java -version

RUN ln -sf /usr/bin/python2.7 /usr/bin/python

RUN adduser --disabled-password --gecos "" ubuntu \
    && usermod -aG sudo ubuntu \
    && echo "ubuntu     ALL=(ALL) NOPASSWD:/usr/bin/python" >> /etc/sudoers \
    && usermod -aG docker ubuntu \
    && su - ubuntu

RUN git clone --branch master --single-branch https://github.com/riptano/ccm.git && \
    cd ccm && \
    sudo python setup.py install

# Create clusters to pre-download necessary artifacts
RUN ccm create -v 4.0-beta4 stargate_40 && ccm remove stargate_40
RUN ccm create -v 3.11.8 stargate_311 && ccm remove stargate_311
RUN ccm create -v 6.8.5 --dse stargate_dse68 && ccm remove stargate_dse68

CMD ["/bin/bash"]