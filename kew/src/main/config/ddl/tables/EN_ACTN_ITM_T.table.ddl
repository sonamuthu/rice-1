CREATE TABLE EN_ACTN_ITM_T (
	ACTN_ITM_ID 		    NUMBER(14) NOT NULL,
	ACTN_ITM_PRSN_EN_ID     VARCHAR2(30) NOT NULL,
	ACTN_ITM_ASND_DT        DATE NOT NULL,
	ACTN_ITM_RQST_CD        CHAR(1) NOT NULL,
	ACTN_RQST_ID            NUMBER(14) NOT NULL,
	DOC_HDR_ID              NUMBER(14) NOT NULL,
	WRKGRP_ID               NUMBER(14) NULL,
	ROLE_NM 				VARCHAR2(2000) NULL,
	ACTN_ITM_DLGN_PRSN_EN_ID VARCHAR2(30) NULL,
    ACTN_ITM_DLGN_WRKGRP_ID NUMBER(14) NULL,
	DOC_TTL			        VARCHAR2(255) NULL,
	DOC_TYP_LBL_TXT         VARCHAR2(255) NOT NULL,
	DOC_TYP_HDLR_URL_ADDR   VARCHAR2(255) NOT NULL,
	DOC_TYP_NM		        VARCHAR2(255) NOT NULL,
	ACTN_ITM_RESP_ID        NUMBER(14) NOT NULL,
	DLGN_TYP				VARCHAR2(1) NULL,
	DB_LOCK_VER_NBR	        NUMBER(8) DEFAULT 0,
    DTYPE                   VARCHAR2(50),
	CONSTRAINT EN_ACTN_ITM_T_PK PRIMARY KEY (ACTN_ITM_ID)  USING INDEX
)
/