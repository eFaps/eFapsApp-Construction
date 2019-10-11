/*
 * Copyright 2007 - 2015 Jan Moxter
 *
 * All Rights Reserved.
 * This program contains proprietary and trade secret information of
 * Jan Moxter Copyright notice is precautionary only and does not
 * evidence any actual or intended publication of such program.
 *
 */

package org.efaps.esjp.construction;

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;


/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 *
 * @author Jan Moxter
 */
@EFapsUUID("8c4548d8-6d91-4933-a2b7-e5d04affa210")
@EFapsApplication("eFapsApp-Construction")
public class CostEstimate
    extends CostEstimate_Base
{
    /**
     * Instance key.
     */
    public static final String COESTINSTKEY = CostEstimate_Base.COESTINSTKEY;

    /**
     * Cache for calculator key.
     */
    public static final String CALCCACHE = CostEstimate_Base.CALCCACHE;

}
