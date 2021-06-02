/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.indices;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.Client;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Convert MLInputDataset to Dataframe
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Log4j2
public class MLInputDatasetHandler {
    Client client;

    /**
     * Retrieve DataFrame from DataFrameInputDataset
     * @param mlInputDataset MLInputDataset
     * @return DataFrame
     */
    public DataFrame parseDataFrameInput(MLInputDataset mlInputDataset) {
        if (!mlInputDataset.getInputDataType().equals(MLInputDataType.DATA_FRAME)) {
            throw new IllegalArgumentException("Input dataset is not DATA_FRAME type.");
        }
        DataFrameInputDataset inputDataset = (DataFrameInputDataset) mlInputDataset;
        return inputDataset.getDataFrame();
    }

    /**
     * Create DataFrame based on given search query
     * @param mlInputDataset MLInputDataset
     * @param listener ActionListener
     */
    public void parseSearchQueryInput(MLInputDataset mlInputDataset, ActionListener<DataFrame> listener) {
        if (!mlInputDataset.getInputDataType().equals(MLInputDataType.SEARCH_QUERY)) {
            throw new IllegalArgumentException("Input dataset is not SEARCH_QUERY type.");
        }
        SearchQueryInputDataset inputDataset = (SearchQueryInputDataset) mlInputDataset;
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.source(inputDataset.getSearchSourceBuilder());
        List<String> indicesList = inputDataset.getIndices();
        String[] indices = new String[indicesList.size()];
        indices = indicesList.toArray(indices);
        searchRequest.indices(indices);

        client.search(searchRequest, ActionListener.wrap(r -> {
            if (
                    r == null ||
                    r.getHits() == null ||
                    r.getHits().getTotalHits() == null ||
                    r.getHits().getTotalHits().value == 0
            ) {
                // todo: add specific exception
                listener.onFailure(new RuntimeException("No document found"));
                return;
            }
            SearchHits hits = r.getHits();
            List<Map<String, Object>> input = new ArrayList<>();
            SearchHit[] searchHits = hits.getHits();
            for (SearchHit hit : searchHits) {
                input.add(hit.getSourceAsMap());
            }
            DataFrame dataFrame = DataFrameBuilder.load(input);
            listener.onResponse(dataFrame);
            return;
        }, e -> {
            log.error("Failed to search" + e);
            listener.onFailure(e);
        }));
        return;
    }
}