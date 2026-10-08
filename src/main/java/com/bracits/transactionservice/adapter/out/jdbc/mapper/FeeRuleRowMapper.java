package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import com.bracits.transactionservice.domain.fee.model.FeeRule;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@code fee_rule} row → {@link FeeRule}; {@code fee_max} NULL → empty.
 */
public interface FeeRuleRowMapper extends RowMapper<FeeRule> {

}
