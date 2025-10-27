FROM elastic/elasticsearch:9.2.0

RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch analysis-kuromoji
RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch analysis-smartcn
RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch analysis-icu
