package com.edu.csu.web.pet_shop_backend.client;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.edu.csu.web.pet_shop_backend.client.dto.UserPatchDto;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserBriefVo;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;

/**
 * user-service 的消费端适配器：统一拆 Result、统一抛 BusinessException。
 */
@Component
public class UserDirectory {

	private final UserClient userClient;

	public UserDirectory(UserClient userClient) {
		this.userClient = userClient;
	}

	public UserBriefVo get(Long id) {
		Result<UserBriefVo> result = userClient.get(id);
		ensureSuccess(result);
		return result == null ? null : result.getData();
	}

	public Map<Long, UserBriefVo> batch(Collection<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return Map.of();
		}
		List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
		if (distinctIds.isEmpty()) {
			return Map.of();
		}
		Result<List<UserBriefVo>> result = userClient.batch(distinctIds);
		ensureSuccess(result);
		Map<Long, UserBriefVo> map = new LinkedHashMap<>();
		if (result != null && result.getData() != null) {
			for (UserBriefVo item : result.getData()) {
				if (item != null && item.getId() != null) {
					map.put(item.getId(), item);
				}
			}
		}
		return map;
	}

	public void patch(Long id, UserPatchDto dto) {
		Result<Void> result = userClient.patch(id, dto);
		ensureSuccess(result);
	}

	private void ensureSuccess(Result<?> result) {
		if (result == null) {
			throw BusinessException.badRequest("user-service 返回为空");
		}
		if (result.getCode() >= 400) {
			throw new BusinessException(result.getCode(), result.getMessage());
		}
	}
}
