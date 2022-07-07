package moe.caa.multilogin.api.auth.yggdrasil.response;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 登入 HasJoined 阶段从 Yggdrasil 账户验证服务器获取到的有效的结果
 */
@Data
public class HasJoinedResponse {
    private UUID id;
    private String name;
    private Map<String, Property> propertyMap;

    @Override
    public HasJoinedResponse clone() {
        HasJoinedResponse response = new HasJoinedResponse();
        response.id = id;
        response.name = name;
        response.propertyMap = new HashMap<>();
        for (Map.Entry<String, Property> entry : propertyMap.entrySet()) {
            response.propertyMap.put(entry.getKey(), entry.getValue().clone());
        }
        return response;
    }
}
