package com.danawa.search.util;

import com.fasterxml.jackson.core.JsonParser;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.search.*;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Key;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.io.stream.ByteBufferStreamInput;
import org.elasticsearch.common.io.stream.InputStreamStreamInput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.common.xcontent.json.JsonXContentParser;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.RemoteClusterService;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RemoteNodeClient extends NodeClient {
    private static Logger logger = Loggers.getLogger(RemoteNodeClient.class, "");
    private Client nodeClient;
    private String index;
    private String host;
    private int port;

    private String url;


    public RemoteNodeClient(Settings settings, ThreadPool threadPool, String index, String host, int port) {
        super(settings, threadPool);
        this.host = host;
        this.port = port;
        this.url = String.format("http://%s:%d", this.host, this.port);
        this.index = index;
    }

    @Override
    public ActionFuture<SearchResponse> search(SearchRequest request) {
        PlainActionFuture<SearchResponse> result = new PlainActionFuture<>();
        HttpURLConnection co = null;
        BufferedReader reader = null;
        try {
            String scroll = "";
            if (request.scroll() != null) {
                scroll = "scroll=" + request.scroll().keepAlive().toString();
            }
            logger.info("dictionary Remote Search . {}", url);
            co = (HttpURLConnection) new URL(String.format("%s/%s/_search?%s", url, index, scroll)).openConnection();
            co.setRequestMethod("POST");
            co.setRequestProperty("Content-Type", "application/json");
            co.setDoOutput(true);
            co.setDoInput(true);
            co.getOutputStream().write(request.source().toString().getBytes(StandardCharsets.UTF_8));
            co.getOutputStream().flush();
            reader = new BufferedReader(new InputStreamReader(co.getInputStream()));
            StringBuilder sb = new StringBuilder();
            for (String rl; (rl = reader.readLine()) != null;) {
                sb.append(rl);
            }
            NamedXContentRegistry registry = new NamedXContentRegistry(new ArrayList<>());
            XContentParser parser = JsonXContent.jsonXContent.createParser(registry, DeprecationHandler.IGNORE_DEPRECATIONS, sb.toString());
            result.onResponse(SearchResponse.fromXContent(parser));
        } catch (IOException e) {
            logger.warn("", e);
            result.onFailure(e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignore){}
            if (co != null) {
                co.disconnect();
            }
        }
        return result;
    }

    @Override
    public ActionFuture<SearchResponse> searchScroll(SearchScrollRequest request) {
        PlainActionFuture<SearchResponse> result = new PlainActionFuture<>();
        HttpURLConnection co = null;
        BufferedReader reader = null;
        try {
//            logger.info("{}", request.scrollId());
            logger.info("dictionary Remote Search . {}", url);
            co = (HttpURLConnection) new URL(String.format("%s/_search/scroll/%s", url, request.scrollId())).openConnection();
            co.setRequestMethod("POST");
            co.setRequestProperty("Content-Type", "application/json");
            co.setDoInput(true);
            reader = new BufferedReader(new InputStreamReader(co.getInputStream()));
            StringBuilder sb = new StringBuilder();
            for (String rl; (rl = reader.readLine()) != null;) {
                sb.append(rl);
            }
            NamedXContentRegistry registry = new NamedXContentRegistry(new ArrayList<>());
            XContentParser parser = JsonXContent.jsonXContent.createParser(registry, DeprecationHandler.IGNORE_DEPRECATIONS, sb.toString());
            result.onResponse(SearchResponse.fromXContent(parser));
        } catch (IOException e) {
            logger.warn("", e);
            result.onFailure(e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignore){}
            if (co != null) {
                co.disconnect();
            }
        }
        return result;
    }

    @Override
    public ActionFuture<ClearScrollResponse> clearScroll(ClearScrollRequest request) {
        PlainActionFuture<ClearScrollResponse> result = new PlainActionFuture<>();
        HttpURLConnection co = null;
        BufferedReader reader = null;
        try {
            String scrollId = request.getScrollIds().get(0);
            logger.info("clear: {}", scrollId);
            co = (HttpURLConnection) new URL(String.format("%s/_search/scroll/%s", url, scrollId)).openConnection();
            co.setRequestMethod("DELETE");
            co.setRequestProperty("Content-Type", "application/json");
            co.setDoInput(true);
            reader = new BufferedReader(new InputStreamReader(co.getInputStream()));
            StringBuilder sb = new StringBuilder();
            for (String rl; (rl = reader.readLine()) != null;) {
                sb.append(rl);
            }
            NamedXContentRegistry registry = new NamedXContentRegistry(new ArrayList<>());
            XContentParser parser = JsonXContent.jsonXContent.createParser(registry, DeprecationHandler.IGNORE_DEPRECATIONS, sb.toString());
            result.onResponse(ClearScrollResponse.fromXContent(parser));
        } catch (IOException e) {
            logger.warn("", e);
            result.onFailure(e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignore){}
            if (co != null) {
                co.disconnect();
            }
        }
        return result;
    }


    public long count(String index, MatchQueryBuilder matchQuery) {
        PlainActionFuture<ClearScrollResponse> result = new PlainActionFuture<>();
        HttpURLConnection co = null;
        BufferedReader reader = null;
        try {

            co = (HttpURLConnection) new URL(String.format("%s/%s/_count", url, index)).openConnection();
            co.setRequestMethod("POST");
            co.setRequestProperty("Content-Type", "application/json");
            co.setDoOutput(true);
            co.setDoInput(true);
            co.getOutputStream().write(matchQuery.toString().getBytes(StandardCharsets.UTF_8));
            co.getOutputStream().flush();

            reader = new BufferedReader(new InputStreamReader(co.getInputStream()));
            StringBuilder sb = new StringBuilder();
            for (String rl; (rl = reader.readLine()) != null;) {
                sb.append(rl);
            }
            NamedXContentRegistry registry = new NamedXContentRegistry(new ArrayList<>());
            XContentParser parser = JsonXContent.jsonXContent.createParser(registry, DeprecationHandler.IGNORE_DEPRECATIONS, sb.toString());
            result.onResponse(ClearScrollResponse.fromXContent(parser));
        } catch (IOException e) {
            logger.warn("", e);
            result.onFailure(e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignore){}
            if (co != null) {
                co.disconnect();
            }
        }
        return 0;
    }
}
