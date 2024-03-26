/*
 * Copyright © 2003 - 2024 The eFaps Team (-)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.efaps.esjp.construction.util;

import java.util.UUID;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.api.annotation.EFapsSysConfAttribute;
import org.efaps.api.annotation.EFapsSystemConfiguration;
import org.efaps.esjp.admin.common.systemconfiguration.BooleanSysConfAttribute;
import org.efaps.esjp.admin.common.systemconfiguration.IntegerSysConfAttribute;
import org.efaps.esjp.admin.common.systemconfiguration.SysConfLink;
import org.efaps.util.cache.CacheReloadException;


@EFapsUUID("4417959d-8440-4077-8c7f-6ea1e3f8e36d")
@EFapsApplication("eFapsApp-Construction")
@EFapsSystemConfiguration("c3af33eb-3090-4763-a12d-da176cf51ad9")
public class Construction
{

    /** The base. */
    public static final String BASE = "org.efaps.construction.";

    /** Construction-Configuration. */
    public static final UUID SYSCONFUUID = UUID.fromString("c3af33eb-3090-4763-a12d-da176cf51ad9");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute VERIFICATIONLISTALLOWGENERIC = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "VerificationList.AllowGeneric")
                    .description("Allow Generic Products to be used in a Verification List.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute VERIFICATIONLISTALLOWMANUAL = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "VerificationList.AllowManual")
                    .description("Make the VerificationList editable by hand..");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute PRODUCTPRICEPERPROJECT = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "ProductPriceIsPerProject")
                    .description("Product Price Is Per Project.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final IntegerSysConfAttribute DEFAULTCOSTESTIMATEPOSQTY = new IntegerSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "CostEstimate.DefaultPositionQuantity")
                    .description("Default Position Quantity for Costestimate.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute COSTESTIMATERELPRODREQ = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "CostEstimate.Relate2ProductRequest")
                    .description("Default Position Quantity for Costestimate.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute COSTESTIMATERELORDEROUT = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "CostEstimate.Relate2OrderOutbound")
                    .description("Default Position Quantity for Costestimate.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute COSTESTIMATERELSRVORDEROUT = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "CostEstimate.Relate2ServiceOrderOutbound")
                    .description("Default Position Quantity for Costestimate.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute ENTRYSHEETRELPRODREQ = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "EntrySheet.Relate2ProductRequest")
                    .description("Default Position Quantity for Costestimate.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute ENTRYSHEETRELORDEROUT = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "EntrySheet.Relate2OrderOutbound")
                    .description("Default Position Quantity for Costestimate.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute ENTRYSHEETRELSRVORDEROUT = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "EntrySheet.Relate2ServiceOrderOutbound")
                    .description("Default Position Quantity for Costestimate.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final SysConfLink DEFAULTTEMPLATEPARTLIST = new SysConfLink()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "TemplatePartList.DefaultInstance")
                    .description("Default Instance for TemplatePartList.");

    /**
     * Singelton.
     */
    private Construction()
    {
    }

    /**
     * Gets the SystemConfiguration.
     *
     * @return the SystemConfigruation for Assets
     * @throws CacheReloadException on error
     */
    public static SystemConfiguration getSysConfig()
        throws CacheReloadException
    {
        return SystemConfiguration.get(SYSCONFUUID);
    }
}
