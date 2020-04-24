package com.danawa.search.analysis.product;

import java.io.IOException;

import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.Table;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.PluginInfo;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.action.RestActionListener;
import org.elasticsearch.rest.action.RestResponseListener;
import org.elasticsearch.rest.action.cat.RestTable;

public class ProductNameAnalysisAction extends BaseRestHandler {

	@Inject
	ProductNameAnalysisAction(Settings settings, RestController controller) {
		controller.registerHandler(Method.GET, "/_product_name_analysis", this);
	}

	@Override
	public String getName() {
		return "rest_handler_product_name_analysis";
	}

	@Override
	protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
		final ClusterStateRequest stateRequest = new ClusterStateRequest();
		stateRequest.clear().nodes(true);
		stateRequest.local(request.paramAsBoolean("local", stateRequest.local()));
		stateRequest.masterNodeTimeout(request.paramAsTime("master_timeout", stateRequest.masterNodeTimeout()));
		return channel -> client.admin().cluster().state(stateRequest, new RestActionListener<ClusterStateResponse>(channel) {
			@Override
			public void processResponse(ClusterStateResponse clusterStateResponse) throws Exception {
				NodesInfoRequest nodesInfoRequest = new NodesInfoRequest();
				nodesInfoRequest.clear().plugins(true);
				client.admin().cluster().nodesInfo(nodesInfoRequest, new RestResponseListener<NodesInfoResponse>(channel) {
					@Override
					public RestResponse buildResponse(NodesInfoResponse nodesInfoResponse) throws Exception {
						return RestTable.buildResponse(buildTable(request, clusterStateResponse, nodesInfoResponse), channel);
					}
				});
			}
        });
	}

	protected Table getTableWithHeader(final RestRequest request) {
		Table table = new Table();
		table.startHeaders();
		table.addCell("id", "default:false;desc:unique node id");
		table.addCell("name", "alias:n;desc:node name");
		table.addCell("hostName", "alias:n;desc:node host name");
		table.addCell("component", "alias:c;desc:component");
		table.addCell("version", "alias:v;desc:component version");
		table.addCell("description", "alias:d;default:false;desc:plugin details");
		table.endHeaders();
		return table;
	}
	
	private Table buildTable(RestRequest req, ClusterStateResponse state, NodesInfoResponse nodesInfo) {
		DiscoveryNodes nodes = state.getState().nodes();
		Table table = getTableWithHeader(req);
		for (DiscoveryNode node : nodes) {
			NodeInfo info = nodesInfo.getNodesMap().get(node.getId());
			for (PluginInfo pluginInfo : info.getPlugins().getPluginInfos()) {
				table.startRow();
				table.addCell(node.getId());
				table.addCell(node.getName());
				table.addCell(node.getHostName());
				table.addCell(pluginInfo.getName());
				table.addCell(pluginInfo.getVersion());
				table.addCell(pluginInfo.getDescription());
				table.endRow();
			}
		}
		return table;
	}
}