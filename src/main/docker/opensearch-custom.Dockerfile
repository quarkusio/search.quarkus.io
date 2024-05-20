FROM opensearchproject/opensearch:2.14.0

# Workaround for https://github.com/opensearch-project/opensearch-devops/issues/97
RUN chmod -R go=u /usr/share/opensearch

RUN /usr/share/opensearch/bin/opensearch-plugin install --batch analysis-kuromoji
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch analysis-smartcn
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch analysis-icu