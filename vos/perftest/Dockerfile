FROM locustio/locust:latest

USER root

#RUN apk --no-cache add gcc git libffi-dev libxslt-dev make musl-dev openssl-dev
#RUN pip install --pre vos==3.3.a1
RUN pip install -e 'git+https://github.com/opencadc/vostools@newstorage#egg=vostools&subdirectory=vos'

# Make requests trust all certificates
ENV CURL_CA_BUNDLE=

#COPY dev_requirements.txt /tmp/
COPY locustfile.py . 
COPY testers.py .
COPY test_config .
RUN mkdir cert
COPY cert/* cert/

COPY entrypoint.sh /

RUN chmod +x /entrypoint.sh

#RUN pip install -r /tmp/dev_requirements.txt

USER locust

ENTRYPOINT ["/entrypoint.sh"]
#CMD ["./docker_start.sh"]
