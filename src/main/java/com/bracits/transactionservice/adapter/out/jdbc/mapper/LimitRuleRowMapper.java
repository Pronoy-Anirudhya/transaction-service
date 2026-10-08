package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import com.bracits.transactionservice.domain.limit.model.LimitRule;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@code limit_rule} row → {@link LimitRule}.
 */
public interface LimitRuleRowMapper extends RowMapper<LimitRule> {

}
