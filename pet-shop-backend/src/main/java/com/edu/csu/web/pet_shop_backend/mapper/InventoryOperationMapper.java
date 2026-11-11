package com.edu.csu.web.pet_shop_backend.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.edu.csu.web.pet_shop_backend.entity.model.InventoryOperation;

public interface InventoryOperationMapper {

	@Select("""
		SELECT op_id, op_type, status, result_json, user_id, create_time, update_time
		FROM inventory_op_tb
		WHERE op_id = #{opId} AND op_type = #{opType}
		""")
	InventoryOperation selectOne(@Param("opId") String opId, @Param("opType") String opType);

	@Insert("""
		INSERT INTO inventory_op_tb (op_id, op_type, status, result_json, user_id)
		VALUES (#{opId}, #{opType}, #{status}, #{resultJson}, #{userId})
		""")
	int insert(InventoryOperation operation);

	@Update("""
		UPDATE inventory_op_tb
		SET status = #{status}, result_json = #{resultJson}
		WHERE op_id = #{opId} AND op_type = #{opType}
		""")
	int update(InventoryOperation operation);
}
