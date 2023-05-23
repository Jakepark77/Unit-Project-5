package com.kenzie.marketing.referral.service.dao;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.ImmutableMap;
import com.kenzie.marketing.referral.service.exceptions.InvalidDataException;
import com.kenzie.marketing.referral.service.model.ReferralRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class NonCachingReferralDao implements ReferralDao{

    private DynamoDBMapper mapper;
    static final Logger log = LogManager.getLogger();

    public NonCachingReferralDao(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    @Override
    public ReferralRecord addReferral(ReferralRecord referral) {
        try {
            mapper.save(referral, new DynamoDBSaveExpression()
                    .withExpected(ImmutableMap.of(
                            "CustomerId",
                            new ExpectedAttributeValue().withExists(false)
                    )));
        } catch (ConditionalCheckFailedException e) {
            throw new InvalidDataException("Customer has already been referred");
        }

        return referral;
    }
    @Override
    public boolean deleteReferral(ReferralRecord referral) {
        try {
            mapper.delete(referral, new DynamoDBDeleteExpression()
                    .withExpected(ImmutableMap.of(
                            "CustomerId",
                            new ExpectedAttributeValue().withValue(new AttributeValue(referral.getCustomerId())).withExists(true)
                    )));
        } catch (AmazonDynamoDBException e) {
            log.info(e.getMessage());
            log.info(e.getStackTrace());
            return false;
        }

        return true;
    }
    @Override
    public List<ReferralRecord> findByReferrerId(String referrerId) {
        ReferralRecord referralRecord = new ReferralRecord();
        referralRecord.setReferrerId(referrerId);

        DynamoDBQueryExpression<ReferralRecord> queryExpression = new DynamoDBQueryExpression<ReferralRecord>()
                .withHashKeyValues(referralRecord)
                .withIndexName("ReferrerIdIndex")
                .withConsistentRead(false);

        return mapper.query(ReferralRecord.class, queryExpression);
    }
    @Override
    public List<ReferralRecord> findUsersWithoutReferrerId() {
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("attribute_not_exists(ReferrerId)");

        return mapper.scan(ReferralRecord.class, scanExpression);
    }
}
