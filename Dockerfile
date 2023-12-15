FROM opensearchproject/opensearch:2.11.0

RUN /usr/share/opensearch/bin/opensearch-plugin install --batch analysis-kuromoji
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch analysis-smartcn
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch analysis-icu