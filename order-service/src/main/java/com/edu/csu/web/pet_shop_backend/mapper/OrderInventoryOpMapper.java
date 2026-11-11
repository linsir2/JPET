package com.edu.csu.web.pet_shop_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.edu.csu.web.pet_shop_backend.entity.model.OrderInventoryOp;

public interface OrderInventoryOpMapper {

	@Select("""
		SELECT op_id, order_id, action, items_json, status, last_error, create_time, update_time
		FROM order_inventory_op_tb WHERE op_id = #{opId}
		""")
	OrderInventoryOp selectOne(@Param("opId") String opId);

	@Insert("""
		INSERT INTO order_inventory_op_tb (op_id, order_id, action, items_json, status, last_error)
		VALUES (#{opId}, #{orderId}, #{action}, #{itemsJson}, #{status}, #{lastError})
		""")
	int insert(OrderInventoryOp operation);

	@Update("""
		UPDATE order_inventory_op_tb
		SET status = #{status}, last_error = #{lastError}
		WHERE op_id = #{opId}
		""")
	int update(OrderInventoryOp operation);

	@Select("""
		SELECT op_id, order_id, action, items_json, status, last_error, create_time, update_time
		FROM order_inventory_op_tb
		WHERE status IN ('PENDING','FAILED')
		ORDER BY create_time
		LIMIT #{limit}
		""")
	List<OrderInventoryOp> selectPending(@Param("limit") int limit);
}
