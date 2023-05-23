package com.kenzie.marketing.referral.service.dao;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.kenzie.marketing.referral.service.caching.CacheClient;
import com.kenzie.marketing.referral.service.model.ReferralRecord;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.inject.Inject;
import javax.security.auth.callback.CallbackHandler;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CachingReferralDao implements ReferralDao{

    private static final int REFERRAL_READ_TTL = 60 * 60;
    private static final String REFERRAL_KEY = "ReferralKey::%s";

    private final CacheClient cacheClient;
    private final NonCachingReferralDao referralDao;
    private DynamoDBMapper mapper;
    GsonBuilder builder = new GsonBuilder().registerTypeAdapter(
            ZonedDateTime.class,
            new TypeAdapter<ZonedDateTime>() {
                @Override
                public void write(JsonWriter out, ZonedDateTime value) throws IOException {
                    out.value(value.toString());
                }
                @Override
                public ZonedDateTime read(JsonReader in) throws IOException {
                    return ZonedDateTime.parse(in.nextString());
                }
            }
    ).enableComplexMapKeySerialization();
    Gson gson = builder.create();

    private List<ReferralRecord> fromJson(String json) {
        return gson.fromJson(json, new TypeToken<ArrayList<ReferralRecord>>() { }.getType());
    }

    @Inject
    public CachingReferralDao(CacheClient cacheClient, NonCachingReferralDao referralDao){
        this.cacheClient = cacheClient;
        this.referralDao = referralDao;
    }

    @Override
    public ReferralRecord addReferral(ReferralRecord referral){
        cacheClient.invalidate(referral.getReferrerId());
        referralDao.addReferral(referral);
        cacheClient.setValue(referral.getReferrerId(),gson.toJson(referral));
        return referral;
    }

    @Override
    public boolean deleteReferral(ReferralRecord referral){
        cacheClient.invalidate(referral.getReferrerId());
        return referralDao.deleteReferral(referral);
    }

    @Override
    public List<ReferralRecord> findByReferrerId(String referrerId){
        List<ReferralRecord> records = fromJson(cacheClient.getValue(referrerId));
        if(records.isEmpty()){
            return referralDao.findByReferrerId(referrerId);
        }
        return records;
    }

    @Override
    public List<ReferralRecord> findUsersWithoutReferrerId(){
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("attribute_not_exists(ReferrerId)");

        return mapper.scan(ReferralRecord.class, scanExpression);
    }

}
