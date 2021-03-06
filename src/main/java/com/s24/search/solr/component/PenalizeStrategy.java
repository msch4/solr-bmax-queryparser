package com.s24.search.solr.component;

import static com.s24.search.solr.component.BmaxBoostConstants.*;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.s24.search.solr.query.bmax.Terms;
import com.s24.search.solr.util.BmaxDebugInfo;
import org.apache.lucene.analysis.Analyzer;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.util.SolrPluginUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public abstract class PenalizeStrategy {

    private final String q;
    private final String penalizeExtraTerms;
    private final Analyzer penalizeAnalyzer;
    private final String penalizeFields;
    private final ResponseBuilder rb;

    public PenalizeStrategy(final ResponseBuilder rb, final String q, final String penalizeExtraTerms,
                            final Analyzer penalizeAnalyzer, final String penalizeFields) {
        this.q = q;
        this.penalizeExtraTerms = penalizeExtraTerms;
        this.penalizeAnalyzer = penalizeAnalyzer;
        this.penalizeFields = penalizeFields;
        this.rb = rb;
    }

    public void apply(final ModifiableSolrParams queryParams) {
        makePenalizeQueryString().ifPresent(queryString -> addToQuery(queryString, queryParams));
    }

    protected abstract void addToQuery(String penalizeQueryString, ModifiableSolrParams queryParams);

    protected Optional<String> makePenalizeQueryString() {


        final Collection<CharSequence> terms = Terms.collect(q, penalizeAnalyzer);

        // add extra terms
        if (penalizeExtraTerms != null) {
            terms.addAll(Sets.newHashSet(Splitter.on(',').omitEmptyStrings().split(penalizeExtraTerms)));
        }

        // add boosts
        if (!terms.isEmpty()) {

            // join terms once. save cpu.
            final String joinedTerms = Joiner.on(" OR ").join(terms);

            // iterate query fields
            StringBuilder penalizeQueryString = new StringBuilder();
            Map<String, Float> queryFields = SolrPluginUtils.parseFieldBoosts(penalizeFields);
            for (Map.Entry<String, Float> field : queryFields.entrySet()) {
                if (penalizeQueryString.length() > 0) {
                    penalizeQueryString.append(" OR ");
                }

                penalizeQueryString.append(field.getKey());
                penalizeQueryString.append(":(");
                penalizeQueryString.append(joinedTerms);
                penalizeQueryString.append(')');
            }

            // add debug
            if (rb.isDebugQuery()) {
                BmaxDebugInfo.add(rb, COMPONENT_NAME + ".penalize.terms", joinedTerms);
            }

            // append penalizeQueryString query
            return Optional.of(penalizeQueryString.toString());

        } else {
            return Optional.empty();
        }
    }
}
