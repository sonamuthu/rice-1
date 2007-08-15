CREATE TABLE EN_MSG_QUE_T (
   MESSAGE_QUE_ID			  NUMBER(14) NOT NULL,
   MESSAGE_QUE_DT             DATE NOT NULL,
   MESSAGE_EXP_DT			  DATE NULL,
   MESSAGE_QUE_PRIO_NBR       NUMBER(8) NOT NULL,
   MESSAGE_QUE_STAT_CD        CHAR(1) NOT NULL,
   MESSAGE_QUE_RTRY_CNT       NUMBER(8) NOT NULL,
   MESSAGE_QUE_IP_NBR         VARCHAR2(2000) NOT NULL,
   MESSAGE_SERVICE_NM		  VARCHAR2(255),
   MESSAGE_ENTITY_NM 		  VARCHAR2(10) NOT NULL,
   SERVICE_METHOD_NM		  VARCHAR2(2000) NULL,
   VAL_ONE					  VARCHAR2(2000) NULL,
   VAL_TWO					  VARCHAR2(2000) NULL,
   DB_LOCK_VER_NBR	          NUMBER(8) DEFAULT 0,
   CONSTRAINT EN_MSG_QUE_T_PK PRIMARY KEY (MESSAGE_QUE_ID) USING INDEX
)
/