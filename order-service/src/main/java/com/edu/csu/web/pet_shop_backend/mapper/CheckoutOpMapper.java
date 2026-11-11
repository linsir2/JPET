package com.edu.csu.web.pet_shop_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.edu.csu.web.pet_shop_backend.entity.model.CheckoutOp;

public interface CheckoutOpMapper {

	@Select("""
		SELECT op_id, user_id, status, items_json, reserve_result_json, last_error, create_time, update_time
		FROM checkout_op_tb WHERE op_id = #{opId}
		""")
	CheckoutOp selectOne(@Param("opId") String opId);

	@Insert("""
		INSERT INTO checkout_op_tb (op_id, user_id, status, items_json, reserve_result_json, last_error)
		VALUES (#{opId}, #{userId}, #{status}, #{itemsJson}, #{reserveResultJson}, #{lastError})
		""")
	int insert(CheckoutOp operation);

	@Update("""
		UPDATE checkout_op_tb
		SET status = #{status}, reserve_result_json = #{reserveResultJson}, last_error = #{lastError}
		WHERE op_id = #{opId}
		""")
	int update(CheckoutOp operation);

	@Select("""
		SELECT op_id, user_id, status, items_json, reserve_result_json, last_error, create_time, update_time
		FROM checkout_op_tb
		WHERE status IN ('RESERVING','RESERVED','COMPENSATING')
		  AND update_time < DATE_SUB(NOW(), INTERVAL #{minutes} MINUTE)
		ORDER BY update_time
		LIMIT #{limit}
		""")
	List<CheckoutOp> selectStale(@Param("minutes") int minutes, @Param("limit") int limit);
}
