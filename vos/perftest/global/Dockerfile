FROM locustio/locust:latest

USER root

#RUN apk --no-cache add gcc git libffi-dev libxslt-dev make musl-dev openssl-dev
#RUN pip install --pre vos==3.3.a1
RUN pip install -e 'git+https://github.com/opencadc/vostools@newstorage#egg=vostools&subdirectory=vos'

# Make requests trust all certificates
ENV CURL_CA_BUNDLE=

RUN mkdir /locust
WORKDIR /locust
COPY locustfile.py . 
COPY testers.py .
RUN mkdir config
COPY config/test_config config/

COPY entrypoint.sh /

RUN chmod +x /entrypoint.sh

USER locust

ENTRYPOINT ["/entrypoint.sh"]
