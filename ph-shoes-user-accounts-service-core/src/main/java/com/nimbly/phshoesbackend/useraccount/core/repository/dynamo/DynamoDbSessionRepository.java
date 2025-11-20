package com.nimbly.phshoesbackend.useraccount.core.repository.dynamo;

import com.nimbly.phshoesbackend.useraccount.core.model.SessionItem;
import com.nimbly.phshoesbackend.useraccount.core.model.dynamo.SessionAttrs;
import com.nimbly.phshoesbackend.useraccount.core.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
@Repository
@RequiredArgsConstructor
public class DynamoDbSessionRepository implements SessionRepository {

    private final DynamoDbEnhancedClient enhanced;

    private DynamoDbTable<SessionItem> table() {
        return enhanced.table(SessionAttrs.TABLE, TableSchema.fromBean(SessionItem.class));
    }
    private DynamoDbIndex<SessionItem> byUserId() {
        return table().index("gsi_userId");
    }

    @Override
    public void createSession(String sessionId, String userId, long expiresAtEpochSec, String ip, String userAgent) {
        var item = new SessionItem();
        item.setSessionId(sessionId);
        item.setUserId(userId);
        item.setCreatedAt(Instant.parse(Instant.now().toString()));
        item.setExpiresAt(expiresAtEpochSec);
        item.setIp(ip);
        item.setUserAgent(userAgent);
        table().putItem(item);
    }

    @Override
    public boolean isSessionActive(String sessionId) {
        var out = table().getItem(r -> r.key(Key.builder().partitionValue(sessionId).build()).consistentRead(true));
        if (out == null || out.getExpiresAt() == null) return false;
        return out.getExpiresAt() > Instant.now().getEpochSecond();
    }

    @Override
    public void revokeSession(String sessionId) {
        table().deleteItem(r -> r.key(Key.builder().partitionValue(sessionId).build()));
    }

    @Override
    public List<String> listActiveSessionIdsByUser(String userId, int limit) {
        var now = Instant.now().getEpochSecond();
        var out = new ArrayList<String>();
        var q = byUserId().query(r -> r
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()))
                .limit(limit <= 0 ? 25 : limit)
        );
        for (Page<SessionItem> page : q) {
            for (SessionItem si : page.items()) {
                if (si.getExpiresAt() != null && si.getExpiresAt() > now) {
                    out.add(si.getSessionId());
                    if (out.size() >= (limit <= 0 ? 25 : limit)) return out;
                }
            }
        }
        return out;
    }
}