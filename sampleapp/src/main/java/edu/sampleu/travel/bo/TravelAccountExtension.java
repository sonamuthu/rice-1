/*
 * Copyright 2006-2008 The Kuali Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.opensource.org/licenses/ecl2.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.sampleu.travel.bo;

import java.util.LinkedHashMap;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.kuali.rice.kns.bo.PersistableBusinessObjectExtensionBase;

@Entity
@Table(name="TRV_ACCT_EXT")
public class TravelAccountExtension extends PersistableBusinessObjectExtensionBase {
    
    @Id
	@Column(name="acct_num")
	private String number;
    
    @Column(name="acct_type")
	private String accountTypeCode;
    
    @OneToOne(fetch=FetchType.EAGER)
	@JoinColumn(name="acct_type", insertable=false, updatable=false)
	private TravelAccountType accountType; 
    
    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    @Override
    protected LinkedHashMap toStringMapper() {
        LinkedHashMap propMap = new LinkedHashMap();
        propMap.put("number", getNumber());
        propMap.put("accountTypeCode", getAccountTypeCode());
        return propMap;
    }

    public String getAccountTypeCode() {
        return accountTypeCode;
    }

    public void setAccountTypeCode(String accountTypeCode) {
        this.accountTypeCode = accountTypeCode;
    }

	public TravelAccountType getAccountType() {
		return accountType;
	}

	public void setAccountType(TravelAccountType accountType) {
		this.accountType = accountType;
	}

 
}