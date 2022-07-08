# Elasticsearch NLP Plugin
An Elasticsearch Plugin that Integrates NLP Capabilities into IR Scoring Algorithms

## Pre-requisites
This plugin requires Java 8+

This plugin currently supports and requires Elasticsearch 7.3.0. 

Due to fundamental changes in underlying lucene data structures 
between versions, it is currently unfeasible to support multiple versions of ES, although that may change in the future.

For the purposes of backreferencing, the supported Elasticsearch versions of each major release are listed below,
in reverse chronological order, although features are not backported between versions
- v1.0.2 - Elasticsearch 7.3.0
- v1.0.1 - Elasticsearch 7.3.0
- v1.0.0 - Elasticsearch 6.6.0

## Installation
1. Please direct yourself to the [releases page](https://github.com/OHNLP/elasticsearch_nlp_plugin/releases) and download the `ES-NLP-PLUGIN.zip`
for your desired release.
2. Follow the installation steps for installing elasticsearch plugins from filesystem [here](https://www.elastic.co/guide/en/elasticsearch/plugins/current/plugin-management-custom-url.html), directing it at the downloaded zip file
3. Copy `config.yml` in the zip to `<your_es_directory>/config/elasticsearch-nlp-plugin.yml` and edit appropriately
4. Documents need to be indexed using the `nlp` tokenizer

## For developers
Making customizations to this codebase is fairly straightforward - you will need JDK 8+, Apache Maven, and Apache Ant
1. First, clone this repository and make your desired changes. 
2. Run `mvn clean install` to generate the plugin JAR
3. Run `ant dist` to generate the distribution zip that is consumed by Elasticsearch's plugin installer
4. Follow the installation steps for installing elasticsearch plugins from filesystem [here](https://www.elastic.co/guide/en/elasticsearch/plugins/current/plugin-management-custom-url.html), directing it at the downloaded zip file

## Reference
>A. Wen, Y. Wang, V. C. Kaggal, S. Liu, H. Liu and J. Fan, "Enhancing Clinical Information Retrieval through Context-Aware Queries and Indices," 2019 IEEE International Conference on Big Data (Big Data), Los Angeles, CA, USA, 2019, pp. 2800-2807, doi: 10.1109/BigData47090.2019.9006241.

