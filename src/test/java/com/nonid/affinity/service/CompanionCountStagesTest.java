package com.nonid.affinity.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CompanionCountStagesTest {
 @Test void agreementBoundaries(){for(int n:new int[]{-1,0})assertEquals(0,CompanionCountStages.agreement(n));assertEquals(1,CompanionCountStages.agreement(1));assertEquals(1,CompanionCountStages.agreement(4));assertEquals(2,CompanionCountStages.agreement(5));assertEquals(2,CompanionCountStages.agreement(9));assertEquals(3,CompanionCountStages.agreement(10));assertEquals(3,CompanionCountStages.agreement(Integer.MAX_VALUE));}
 @Test void giftCountsAreIndividualStages(){assertEquals(0,CompanionCountStages.gifts(-1));for(int n=0;n<=6;n++)assertEquals(n,CompanionCountStages.gifts(n));assertEquals(6,CompanionCountStages.gifts(7));assertEquals(6,CompanionCountStages.gifts(Integer.MAX_VALUE));}
}
