FROM elastic/elasticsearch:9.0.2

RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch analysis-kuromoji
RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch analysis-smartcn
RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch analysis-icu
