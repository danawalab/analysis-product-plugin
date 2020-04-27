package com.danawa.search.analysis.product;

import java.io.IOException;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;

public class ProductNameAnalysisAction extends BaseRestHandler {

	private static final String BASE_URI = "/_product-name-analysis";

	@Inject
	ProductNameAnalysisAction(Settings settings, RestController controller) {
		controller.registerHandler(Method.GET, BASE_URI + "/{action}", this);
		controller.registerHandler(Method.GET, BASE_URI, this);
	}

	@Override
	public String getName() {
		return "rest_handler_product-name-analysis";
	}

	@Override
	protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
		final String action = request.param("action");
		return channel -> {
			XContentBuilder builder = channel.newBuilder();
			builder.startObject()
				.field("message", "OK")
				.field("action", action)
			.endObject();
			channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
		};
	}
}