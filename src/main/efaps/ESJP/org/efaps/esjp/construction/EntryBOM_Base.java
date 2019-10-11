/*
 * Copyright 2007 - 2016 Jan Moxter
 *
 * All Rights Reserved.
 * This program contains proprietary and trade secret information of
 * moxter.net. Copyright notice is precautionary only and does not
 * evidence any actual or intended publication of such program.
 *
 */

package org.efaps.esjp.construction;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.ui.IUIValue;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.field.Field;
import org.efaps.admin.ui.field.FieldPicker;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIConstruction;
import org.efaps.esjp.ci.CIFormConstruction;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.construction.util.Construction;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.sales.Calculator;
import org.efaps.esjp.sales.ICalculatorConfig;
import org.efaps.esjp.sales.document.AbstractDocumentSum;
import org.efaps.esjp.sales.document.AbstractDocument_Base;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author Jan Moxter
 */
@EFapsUUID("5bb305d9-a10c-4152-908e-1594a1f5a442")
@EFapsApplication("eFapsApp-Construction")
public abstract class EntryBOM_Base
    extends AbstractDocumentSum
{

    /** The postypes. */
    private static Set<IPositionType> POSTYPES = new LinkedHashSet<IPositionType>();
    static {
        for (final IPositionType posType : PositionType.values()) {
            EntryBOM_Base.POSTYPES.add(posType);
        }
    }

    /**
     * Gets the position types.
     *
     * @return the position types
     */
    protected static Collection<IPositionType> getPositionTypes()
    {
        return EntryBOM_Base.POSTYPES;
    }

    /**
     * Gets the position type for a give product instance.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _prodInst the prod inst
     * @return the position type
     */
    protected static IPositionType getPositionType(final Parameter _parameter,
                                                   final Instance _prodInst)
    {
        return getPositionType(_parameter, _prodInst, null);
    }

    /**
     * Gets the position type for a givne product instance.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _prodInst the prod inst
     * @param _clazzes the clazzes
     * @return the position type
     */
    protected static IPositionType getPositionType(final Parameter _parameter,
                                                   final Instance _prodInst,
                                                   final List<Classification> _clazzes)
    {
        // first check if unique type can be found
        IPositionType ret = null;
        int i = 0;
        for (final IPositionType posType : getPositionTypes()) {
            if (posType.getTypes().contains(_prodInst.getType())) {
                ret = posType;
                i++;
            }
        }
        if (i != 1) {
            if (_clazzes != null && !_clazzes.isEmpty()) {
                for (final IPositionType postype : EntryBOM.getPositionTypes()) {
                    if (postype.getClassifications().contains(_clazzes.get(0))) {
                        ret = postype;
                        break;
                    }
                }
            }
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return map for update event
     * @throws EFapsException on error
     */
    public Return updateFields4EfficiencyOverAll(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, getListMap4Update(_parameter));
        return retVal;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return map for update event
     * @throws EFapsException on error
     */
    public Return updateFields4EfficiencyPersonal(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, getListMap4Update(_parameter));
        return retVal;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return map for update event
     * @throws EFapsException on error
     */
    public Return updateFields4EfficiencyTools(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, getListMap4Update(_parameter));
        return retVal;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return map for update event
     * @throws EFapsException on error
     */
    public Return updateFields4Base(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, getListMap4Update(_parameter));
        return retVal;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return map for update event
     * @throws EFapsException on error
     */
    protected List<Map<String, String>> getListMap4Update(final Parameter _parameter)
        throws EFapsException
    {
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final List<Calculator> calcList = analyseTable(_parameter, null);
        IPositionType currPosType = null;
        boolean first = true;
        int i = 0;
        for (final Calculator calc : calcList) {
            if (!calc.isEmpty()) {
                final IPositionType posType = ((BomCalculator) calc).getPosType();
                if (!posType.equals(currPosType)) {
                    i = 0;
                }
                final Map<String, String> map = new HashMap<String, String>();
                map.put(EFapsKey.FIELDUPDATE_USEIDX.getKey(), String.valueOf(i));
                map.put("quantity" + posType.getSuffix(), calc.getQuantityStr());
                map.put("unitPrice" + posType.getSuffix(), calc.getNetUnitPriceFmtStr());
                map.put("price" + posType.getSuffix(), calc.getNetPriceFmtStr());
                map.put("factor" + posType.getSuffix(), ((BomCalculator) calc).getFactorStr());
                map.put("base" + posType.getSuffix(), ((BomCalculator) calc).getBaseStr());
                if (first) {
                    map.put(CIFormConstruction.Construction_EntryPartListForm.manualPercentSum.name,
                                    NumberFormatter.get().getTwoDigitsFormatter()
                                                    .format(getManualPercentAmount(_parameter, calcList, false)));
                    map.put(CIFormConstruction.Construction_EntryPartListForm.total.name, getNetTotalFmtStr(_parameter, calcList));
                    map.put(CIFormConstruction.Construction_EntryPartListForm.personalTotal.name,
                                    getSubTotalFmtStr(_parameter, calcList, PositionType.PERSONAL));
                    map.put(CIFormConstruction.Construction_EntryPartListForm.toolTotal.name,
                                    getSubTotalFmtStr(_parameter, calcList, PositionType.TOOLS));
                    map.put(CIFormConstruction.Construction_EntryPartListForm.materialTotal.name,
                                    getSubTotalFmtStr(_parameter, calcList, PositionType.MATERIAL));
                    map.put(CIFormConstruction.Construction_EntryPartListForm.contractTotal.name,
                                    getSubTotalFmtStr(_parameter, calcList, PositionType.CONTRACT));
                    first = false;
                }
                list.add(map);
                currPosType = posType;
            }
            i++;
        }
        return list;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return map for update event
     * @throws EFapsException on error
     */
    public Return updateFields4Factor(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        final List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        final Map<String, Object> map = new HashMap<String, Object>();

        final int selected = getSelectedRow(_parameter);
        final List<Calculator> calcList = analyseTable(_parameter, null);
        final Calculator cal = calcList.get(selected);
        if (calcList.size() > 0) {
            add2Map4UpdateField(_parameter, map, calcList, cal, true);
            list.add(map);
            retVal.put(ReturnValues.VALUES, list);
        }
        return retVal;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return map for update event
     * @throws EFapsException on error
     */
    public Return updateFields4UnitPrice(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        final List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        final Map<String, Object> map = new HashMap<String, Object>();

        final int selected = getSelectedRow(_parameter);
        final List<Calculator> calcList = analyseTable(_parameter, null);
        final Calculator cal = calcList.get(selected);
        if (calcList.size() > 0) {
            add2Map4UpdateField(_parameter, map, calcList, cal, true);
            list.add(map);
            retVal.put(ReturnValues.VALUES, list);
        }
        return retVal;
    }

    @Override
    protected boolean add2QueryBldr4AutoComplete4Product(final Parameter _parameter,
                                                         final QueryBuilder _queryBldr)
        throws EFapsException
    {
        final IPositionType posType = evalPostionType(_parameter);
        _queryBldr.addWhereAttrInQuery(CIProducts.ProductAbstract.ID, getProductAttrQuery(_parameter, posType));
        return true;
    }

    @Override
    protected void add2UpdateField4Product(final Parameter _parameter,
                                           final Map<String, Object> _map,
                                           final Instance _prodInst)
        throws EFapsException
    {
        super.add2UpdateField4Product(_parameter, _map, _prodInst);
        final IPositionType posType = evalPostionType(_parameter);
        _map.put("productDesc" + posType.getSuffix(), _map.get("productDesc"));
        _map.put("uoM" + posType.getSuffix(), _map.get("uoM"));
    }

    @Override
    protected void add2Map4UpdateField(final Parameter _parameter,
                                       final Map<String, Object> _map,
                                       final List<Calculator> _calcList,
                                       final Calculator _cal,
                                       final boolean _addTotals)
        throws EFapsException
    {
        final IPositionType posType = evalPostionType(_parameter);
        final Calculator cal = evalCalculator4Selected(_parameter, posType, _calcList, null);

        _map.put("quantity" + posType.getSuffix(), cal.getQuantityStr());
        _map.put("unitPrice" + posType.getSuffix(), cal.getNetUnitPriceFmtStr());
        _map.put("price" + posType.getSuffix(), cal.getNetPriceFmtStr());
        _map.put("factor" + posType.getSuffix(), ((BomCalculator) cal).getFactorStr());
        _map.put("base" + posType.getSuffix(), ((BomCalculator) cal).getBaseStr());

        _map.put(CIFormConstruction.Construction_EntryPartListForm.manualPercentSum.name,
                        NumberFormatter.get().getTwoDigitsFormatter()
                                        .format(getManualPercentAmount(_parameter, _calcList, false)));
        _map.put(CIFormConstruction.Construction_EntryPartListForm.total.name, getNetTotalFmtStr(_parameter, _calcList));
        _map.put(CIFormConstruction.Construction_EntryPartListForm.personalTotal.name,
                        getSubTotalFmtStr(_parameter, _calcList, PositionType.PERSONAL));
        _map.put(CIFormConstruction.Construction_EntryPartListForm.toolTotal.name,
                        getSubTotalFmtStr(_parameter, _calcList, PositionType.TOOLS));
        _map.put(CIFormConstruction.Construction_EntryPartListForm.materialTotal.name,
                        getSubTotalFmtStr(_parameter, _calcList, PositionType.MATERIAL));
        _map.put(CIFormConstruction.Construction_EntryPartListForm.contractTotal.name,
                        getSubTotalFmtStr(_parameter, _calcList, PositionType.CONTRACT));
    }

    /**
     * Eval calculator4 selected.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _posType the pos type
     * @param _calcList the calc list
     * @param _selected the selected
     * @return the calculator
     * @throws EFapsException on error
     */
    protected Calculator evalCalculator4Selected(final Parameter _parameter,
                                                 final IPositionType _posType,
                                                 final List<Calculator> _calcList,
                                                 final Integer _selected)
        throws EFapsException
    {
        final int selected = _selected == null ? getSelectedRow(_parameter) : _selected;
        Calculator ret = null;
        int i = 0;
        for (final Calculator calc : _calcList) {
            if (((BomCalculator) calc).getPosType().equals(_posType)) {
                if (i == selected) {
                    ret = calc;
                    break;
                } else {
                    i++;
                }
            }
        }
        if (ret == null) {
            ret = getEmptyCalculator(_parameter, _posType);
        }
        return ret;
    }

    /**
     * Evaluate the IPositionType from information of the UserInterface.
     *
     * @param _parameter Parameter as passed by the eFasp API
     * @return IPositionType for the position
     */
    protected IPositionType evalPostionType(final Parameter _parameter)
    {
        IPositionType ret = null;
        Field field = null;
        if (_parameter.get(ParameterValues.UIOBJECT) instanceof IUIValue) {
            field = ((IUIValue) _parameter.get(ParameterValues.UIOBJECT)).getField();
        } else if (_parameter.get(ParameterValues.UIOBJECT) instanceof FieldPicker) {
            field = (FieldPicker) _parameter.get(ParameterValues.UIOBJECT);
        } else if (_parameter.get(ParameterValues.UIOBJECT) instanceof Field) {
            field = (Field) _parameter.get(ParameterValues.UIOBJECT);
        }
        if (field != null) {
            final String fieldName = field.getName();
            if (fieldName.endsWith(PositionType.MATERIAL.getSuffix())) {
                ret = PositionType.MATERIAL;
            } else if (fieldName.endsWith(PositionType.PERSONAL.getSuffix())) {
                ret = PositionType.PERSONAL;
            } else if (fieldName.endsWith(PositionType.TOOLS.getSuffix())) {
                ret = PositionType.TOOLS;
            } else {
                ret = PositionType.CONTRACT;
            }
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the efaps API
     * @param _posType Poensition type
     * @return attributeQuery to be used
     * @throws EFapsException on error
     */
    public AttributeQuery getProductAttrQuery(final Parameter _parameter,
                                              final IPositionType _posType)
        throws EFapsException
    {
        final Iterator<Type> typeIter = _posType.getTypes().iterator();
        final QueryBuilder queryBuilder = new QueryBuilder(typeIter.next());
        while (typeIter.hasNext()) {
            queryBuilder.addType(typeIter.next());
        }
        if (_posType.isClassified()) {
            queryBuilder.addWhereClassification(_posType.getClassifications()
                            .toArray(new Classification[_posType.getClassifications().size()]));
        }
        return queryBuilder.getAttributeQuery(CIProducts.ProductAbstract.ID);
    }

    /**
     * Gets the sub total fmt str.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _calcList the calc list
     * @param _iPositionType the i position type
     * @return the sub total fmt str
     * @throws EFapsException on error
     */
    protected String getSubTotalFmtStr(final Parameter _parameter,
                                       final List<Calculator> _calcList,
                                       final IPositionType _iPositionType)
        throws EFapsException
    {
        return NumberFormatter.get().getFrmt4Total(getTypeName4SysConf(_parameter))
                        .format(getSubTotal(_parameter, _calcList, _iPositionType));
    }

    /**
     * Gets the sub total.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _calcList the calc list
     * @param _positionType the position type
     * @return the sub total
     * @throws EFapsException on error
     */
    protected BigDecimal getSubTotal(final Parameter _parameter,
                                     final List<Calculator> _calcList,
                                     final IPositionType _positionType)
        throws EFapsException
    {
        BigDecimal ret = BigDecimal.ZERO;
        for (final Calculator calc : _calcList) {
            if (((BomCalculator) calc).getPosType().equals(_positionType)) {
                ret = ret.add(calc.getNetPrice());
            }
        }
        if (_positionType.equals(PositionType.TOOLS)) {
            ret = ret.add(getManualPercentAmount(_parameter, _calcList, false));
        }
        return ret;
    }

    /**
     * Gets the manual percent amount.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _calcList the calc list
     * @param _cross the cross
     * @return the manual percent amount
     * @throws EFapsException on error
     */
    protected BigDecimal getManualPercentAmount(final Parameter _parameter,
                                                final List<Calculator> _calcList,
                                                final boolean _cross)
        throws EFapsException
    {
        final String manualPercent = _parameter.getParameterValue(CIFormConstruction.Construction_EntryPartListForm.manualPercent.name);
        BigDecimal ret = BigDecimal.ZERO;
        if (manualPercent != null && !manualPercent.isEmpty()) {
            try {
                final BigDecimal percent = (BigDecimal) NumberFormatter.get().getTwoDigitsFormatter()
                                .parse(manualPercent);
                for (final Calculator calc : _calcList) {
                    if (((BomCalculator) calc).getPosType().equals(PositionType.PERSONAL)) {
                        if (_cross) {
                            ret = ret.add(calc.getCrossPrice());
                        } else {
                            ret = ret.add(calc.getNetPrice());
                        }
                    }
                }
                ret = ret.multiply(percent.setScale(8).divide(new BigDecimal(100), BigDecimal.ROUND_HALF_UP));
            } catch (final ParseException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    @Override
    public BigDecimal getNetTotal(final Parameter _parameter,
                                  final List<Calculator> _calcList)
        throws EFapsException
    {
        BigDecimal ret = super.getNetTotal(_parameter, _calcList);
        ret = ret.add(getManualPercentAmount(_parameter, _calcList, false));

        return ret;
    }

    @Override
    public BigDecimal getCrossTotal(final Parameter _parameter,
                                    final List<Calculator> _calcList)
        throws EFapsException
    {
        BigDecimal ret = super.getCrossTotal(_parameter, _calcList);
        ret = ret.add(getManualPercentAmount(_parameter, _calcList, true));

        return ret;
    }

    @Override
    public List<Calculator> analyseTable(final Parameter _parameter,
                                         final Integer _row4priceFromDB)
        throws EFapsException
    {
        final List<Calculator> ret = new ArrayList<Calculator>();

        @SuppressWarnings("unchecked")
        final List<Calculator> oldCalcs = (List<Calculator>) Context.getThreadContext().getSessionAttribute(
                        AbstractDocument_Base.CALCULATOR_KEY);
        final IPositionType curPosType = evalPostionType(_parameter);
        for (final IPositionType posType : EntryBOM.getPositionTypes()) {
            final String[] unitPrices = _parameter.getParameterValues("unitPrice" + posType.getSuffix());
            final String[] quantities = _parameter.getParameterValues("quantity" + posType.getSuffix());
            final String[] factors = _parameter.getParameterValues("factor" + posType.getSuffix());
            final String[] oids = _parameter.getParameterValues("product" + posType.getSuffix());
            final String[] uoms = _parameter.getParameterValues("uoM" + posType.getSuffix());
            final String[] remarks = _parameter.getParameterValues("remark" + posType.getSuffix());
            final String efficiencyOverAll = _parameter
                            .getParameterValue(CIFormConstruction.Construction_EntryPartListForm.efficiencyOverAll.name);
            final String efficiencyPersonal = _parameter
                            .getParameterValue(CIFormConstruction.Construction_EntryPartListForm.efficiencyPersonal.name);
            final String efficiencyTools = _parameter
                            .getParameterValue(CIFormConstruction.Construction_EntryPartListForm.efficiencyTools.name);
            final String baseValue = _parameter
                            .getParameterValue(CIFormConstruction.Construction_EntryPartListForm.base.name);
            if (quantities != null) {
                for (int i = 0; i < quantities.length; i++) {
                    Calculator oldCalc = null;
                    if (oldCalcs.size() > 0 && oldCalcs.size() > i) {
                        oldCalc = oldCalcs.get(i);
                    }
                    if (unitPrices[i].length() > 0 || quantities[i].length() > 0 || factors[i].length() > 0
                                    || oids[i].length() > 0) {
                        final boolean priceFromDB = posType.equals(curPosType) && _row4priceFromDB != null
                                        && (_row4priceFromDB.equals(i) || _row4priceFromDB.equals(-1));
                        final BomCalculator calc = getCalculator(_parameter, posType, oldCalc, oids[i],
                                        quantities[i], unitPrices[i], factors[i], efficiencyOverAll,
                                        efficiencyPersonal, efficiencyTools, baseValue,
                                        uoms[i], remarks != null ? remarks[i] : null,
                                        priceFromDB);
                        ret.add(calc);
                    } else {
                        ret.add(getEmptyCalculator(_parameter, posType));
                    }
                }
            }
        }
        Context.getThreadContext().setSessionAttribute(AbstractDocument_Base.CALCULATOR_KEY, ret);
        return ret;
    }

    /**
     * Gets the calculators4 entry part list.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _entryPartListInst the entry part list inst
     * @return the calculators4 entry part list
     * @throws EFapsException on error
     */
    public Map<Instance, Calculator> getCalculators4EntryPartList(final Parameter _parameter,
                                                                  final Instance _entryPartListInst)
        throws EFapsException
    {
        final Map<Instance, Calculator> ret = new HashMap<Instance, Calculator>();

        final PrintQuery print = new PrintQuery(_entryPartListInst);
        final SelectBuilder selEffPers = SelectBuilder.get().clazz(CIConstruction.EntryPartList_Class)
                        .attribute(CIConstruction.EntryPartList_Class.EfficiencyPersonal);
        final SelectBuilder selEffTools = SelectBuilder.get().clazz(CIConstruction.EntryPartList_Class)
                        .attribute(CIConstruction.EntryPartList_Class.EfficiencyTools);
        final SelectBuilder selEffOA = SelectBuilder.get().clazz(CIConstruction.EntryPartList_Class)
                        .attribute(CIConstruction.EntryPartList_Class.EfficiencyOverAll);
        print.addSelect(selEffOA, selEffPers, selEffTools);
        print.execute();
        final DecimalFormat formatter = NumberFormatter.get().getFormatter();

        final BigDecimal effPers = print.<BigDecimal>getSelect(selEffPers);
        final BigDecimal effTools = print.<BigDecimal>getSelect(selEffTools);
        final BigDecimal effOA = print.<BigDecimal>getSelect(selEffOA);

        final String efficiencyOverAll = effOA == null ? null : formatter.format(effOA);
        final String efficiencyPers = effPers == null ? null : formatter.format(effPers);
        final String efficiencyTools = effTools == null ? null : formatter.format(effTools);

        final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.EntryBOM);
        queryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.FromLink, _entryPartListInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selProd = SelectBuilder.get().linkto(CIConstruction.EntryBOM.ToLink);
        final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
        final SelectBuilder selProdClass = new SelectBuilder(selProd).clazz().type();
        multi.addSelect(selProdInst, selProdClass);
        multi.addAttribute(CIConstruction.EntryBOM.UnitPrice, CIConstruction.EntryBOM.Factor,
                        CIConstruction.EntryBOM.Quantity, CIConstruction.EntryBOM.UoM, CIConstruction.EntryBOM.Remark);
        multi.execute();
        while (multi.next()) {
            final String unitPrice = formatter.format(multi.<BigDecimal>getAttribute(CIConstruction.EntryBOM.UnitPrice));
            final String quantity = formatter.format(multi.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Quantity));
            final BigDecimal factor = multi.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Factor);
            final String factorStr = factor == null ? "" : formatter.format(factor);
            final Instance prodInst = multi.getSelect(selProdInst);
            final String uom = multi.<Long>getAttribute(CIConstruction.EntryBOM.UoM).toString();
            final String remark = multi.getAttribute(CIConstruction.EntryBOM.Remark);

            final List<Classification> clazzes = multi.<List<Classification>>getSelect(selProdClass);
            final IPositionType posType = EntryBOM.getPositionType(_parameter, prodInst, clazzes);

            final BomCalculator calc = getCalculator(_parameter, posType, null, prodInst.getOid(),
                            quantity, unitPrice, factorStr, efficiencyOverAll, efficiencyPers, efficiencyTools, "",
                            uom, remark, false);
            ret.put(multi.getCurrentInstance(), calc);
        }
        return ret;
    }

    @Override
    public Return getJavaScriptUIValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        final StringBuilder js = new StringBuilder();
        js.append("<script type=\"text/javascript\">\n")
                .append("require([\"dojo/query\",\"dojo/NodeList-traverse\", \"dojo/domReady!\"], function (query) {\n")
                .append("query('[name$=Placeholder]').parent().style(\"width\", \"80%\");\n")
                .append("});\n")
                .append("</script>\n");
        retVal.put(ReturnValues.SNIPLETT, js.toString());
        return retVal;
    }

    /**
     * Gets the empty calculator.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _posType the pos type
     * @return the empty calculator
     * @throws EFapsException on error
     */
    public BomCalculator getEmptyCalculator(final Parameter _parameter,
                                            final IPositionType _posType)
        throws EFapsException
    {
        return new BomCalculator(_posType);
    }

    @Override
    public String getTypeName4SysConf(final Parameter _parameter)
        throws EFapsException
    {
        return CIConstruction.EntryPartList.getType().getName();
    }

    /**
     * @param _posType position type
     * @param _parameter as passed from eFaps API.
     * @param _calc An old calculator.
     * @param _oid The OID of the product.
     * @param _quantity The quantity of the product.
     * @param _unitPrice The unit price of the product.
     * @param _factor The factor of the part list.
     * @param _efficiencyOverAll The efficiency of the part list.
     * @param _efficiencyPersonal The efficiency of the part list.
     * @param _efficiencyTools The efficiency of the part list.
     * @param _baseValue base value for calc
     * @param _uom uom for the line
     * @throws EFapsException on error
     */
    // CHECKSTYLE:OFF
    public BomCalculator getCalculator(final Parameter _parameter,
                                       final IPositionType _posType,
                                       final Calculator _calc,
                                       final String _oid,
                                       final String _quantity,
                                       final String _unitPrice,
                                       final String _factor,
                                       final String _efficiencyOverAll,
                                       final String _efficiencyPersonal,
                                       final String _efficiencyTools,
                                       final String _baseValue,
                                       final String _uom,
                                       final String _remark,
                                       final boolean _priceFromDB)
        throws EFapsException
    // CHECKSTYLE:ON
    {
        return new BomCalculator(_parameter, _posType, _calc, _oid, _quantity, _unitPrice, _factor, _efficiencyOverAll,
                        _efficiencyPersonal, _efficiencyTools, _baseValue, _uom, _remark, _priceFromDB, this);
    }

    /**
     * The Class BomCalculator.
     *
     */
    public class BomCalculator
        extends Calculator
    {
        /**
         *
         */
        private static final long serialVersionUID = 1L;

        /**
         * Multiplication factor.
         */
        private BigDecimal factor = BigDecimal.ONE;

        /**
         * base value.
         */
        private BigDecimal baseValue = BigDecimal.ONE;

        /**
         * Over all efficiency factor.
         */
        private BigDecimal efficiencyOverAll = BigDecimal.ONE;

        /**
         *
         */
        private BigDecimal efficiencyPersonal = BigDecimal.ONE;

        /**
        *
        */
        private BigDecimal efficiencyTools = BigDecimal.ONE;

        /**
         *
         */
        private boolean useEff;

        /**
         * Type of the Position.
         */
        private final IPositionType posType;

        /**
         * UoM of the position.
         */
        private String uom;

        /** The remark. */
        private String remark;

        /**
         * @param _posType position type
         * @param _parameter as passed from eFaps API.
         * @param _calc An old calculator.
         * @param _oid The OID of the product.
         * @param _quantity The quantity of the product.
         * @param _unitPrice The unit price of the product.
         * @param _factor The factor of the part list.
         * @param _efficiencyOverAll The efficiency of the part list.
         * @param _efficiencyPersonal The efficiency of the part list.
         * @param _efficiencyTools The efficiency of the part list.
         * @param _baseValue base value for calc
         * @param _uom uom for the line
         * @param _calculatorUse definition to be used
         * @throws EFapsException on error
         */
        // CHECKSTYLE:OFF
        public BomCalculator(final Parameter _parameter,
                             final IPositionType _posType,
                             final Calculator _calc,
                             final String _oid,
                             final String _quantity,
                             final String _unitPrice,
                             final String _factor,
                             final String _efficiencyOverAll,
                             final String _efficiencyPersonal,
                             final String _efficiencyTools,
                             final String _baseValue,
                             final String _uom,
                             final String _remark,
                             final boolean _priceFromDB,
                             final ICalculatorConfig _calculatorUse)
            throws EFapsException
        // CHECKSTYLE:ON
        {
            super(_parameter, _calc, _oid, _quantity, _unitPrice, "", _priceFromDB, _calculatorUse);
            posType = _posType;
            uom = _uom;
            remark = _remark;
            // check if the price is defined in the current Project
            if (Construction.PRODUCTPRICEPERPROJECT.get()
                            &&  _priceFromDB && (_unitPrice == null || _unitPrice != null && _unitPrice.isEmpty())) {
                BigDecimal price = BigDecimal.ZERO;
                final Instance currentInst = _parameter.getInstance() == null ? _parameter.getCallInstance()
                                : _parameter.getInstance();
                if (currentInst != null && currentInst.getType().isCIType(CIConstruction.EntrySheet)) {
                    final PrintQuery print = new PrintQuery(currentInst);
                    final SelectBuilder selCEInst = SelectBuilder.get().linkfrom(CIConstruction.CostEstimate2EntrySheet.ToLink)
                                    .linkto(CIConstruction.CostEstimate2EntrySheet.FromLink).instance();
                    print.addSelect(selCEInst);
                    print.execute();
                    final Instance ceInst = print.getSelect(selCEInst);

                    final QueryBuilder espQueryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
                    if (ceInst != null && ceInst.isValid()) {
                        final QueryBuilder ce2esQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
                        ce2esQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.FromLink, ceInst);
                        espQueryBldr.addWhereAttrInQuery(CIConstruction.EntrySheetPosition.DocumentAbstractLink,
                                        ce2esQueryBldr.getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.ToLink));
                    } else {
                        espQueryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.DocumentAbstractLink, currentInst);
                    }

                    final QueryBuilder ebQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
                    ebQueryBldr.addWhereAttrInQuery(CIConstruction.EntryBOM.FromLink,
                                    espQueryBldr.getAttributeQuery(CIConstruction.EntrySheetPosition.Product));
                    ebQueryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.ToLink, getProductInstance());
                    final MultiPrintQuery multi = ebQueryBldr.getPrint();
                    multi.addAttribute(CIConstruction.EntryBOM.UnitPrice);
                    multi.execute();
                    if (multi.next()) {
                        price = multi.getAttribute(CIConstruction.EntryBOM.UnitPrice);
                    }
                }
                if (price.compareTo(BigDecimal.ZERO) > 0) {
                    setUnitPrice(price);
                }
            }

            if (_factor != null && !_factor.isEmpty()) {
                try {
                    factor = (BigDecimal) NumberFormatter.get().getFormatter().parse(_factor);
                    useEff = true;
                } catch (final ParseException e) {
                    e.printStackTrace();
                }
            }
            if (_efficiencyOverAll != null && !_efficiencyOverAll.isEmpty()) {
                try {
                    final BigDecimal efficiencyTmp = (BigDecimal) NumberFormatter.get().getFormatter()
                                    .parse(_efficiencyOverAll);
                    if (efficiencyTmp.compareTo(BigDecimal.ZERO) != 0) {
                        efficiencyOverAll = efficiencyTmp;
                    }
                } catch (final ParseException e) {
                    e.printStackTrace();
                }
            }
            if (_efficiencyPersonal != null && !_efficiencyPersonal.isEmpty()) {
                try {
                    final BigDecimal efficiencyTmp = (BigDecimal) NumberFormatter.get().getFormatter()
                                    .parse(_efficiencyPersonal);
                    if (efficiencyTmp.compareTo(BigDecimal.ZERO) != 0) {
                        efficiencyPersonal = efficiencyTmp;
                    }
                } catch (final ParseException e) {
                    e.printStackTrace();
                }
            }

            if (_efficiencyTools != null && !_efficiencyTools.isEmpty()) {
                try {
                    final BigDecimal efficiencyTmp = (BigDecimal) NumberFormatter.get().getFormatter()
                                    .parse(_efficiencyTools);
                    if (efficiencyTmp.compareTo(BigDecimal.ZERO) != 0) {
                        efficiencyTools = efficiencyTmp;
                    }
                } catch (final ParseException e) {
                    e.printStackTrace();
                }
            }

            if (_baseValue != null && !_baseValue.isEmpty()) {
                try {
                    final BigDecimal tmpVal = (BigDecimal) NumberFormatter.get().getFormatter()
                                    .parse(_baseValue);
                    if (tmpVal.compareTo(BigDecimal.ZERO) != 0) {
                        baseValue = tmpVal;
                    }
                } catch (final ParseException e) {
                    e.printStackTrace();
                }
            }

            if (_oid == null || _oid.isEmpty()) {
                setEmpty(true);
            }
        }

        /**
         * @param _posType position type
         * @throws EFapsException on error.
         *
         */
        public BomCalculator(final IPositionType _posType)
            throws EFapsException
        {
            super();
            posType = _posType;
        }

        @Override
        public BigDecimal getQuantity()
        {
            final BigDecimal quanTmp;
            if (useEff) {
                quanTmp = factor.setScale(4).multiply(new BigDecimal(8))
                                .divide(getEfficiencyApplied(), BigDecimal.ROUND_HALF_UP);
            } else {
                quanTmp = super.getQuantity();
            }
            return quanTmp;
        }

        @Override
        public String getQuantityStr()
            throws EFapsException
        {
            final DecimalFormat fomater = (DecimalFormat) NumberFormatter.get().getFormatter().clone();
            fomater.setMaximumFractionDigits(4);
            return fomater.format(getQuantity());
        }

        /**
         * @return BigDecimal with the factor.
         */
        public BigDecimal getFactor()
        {
            return factor;
        }

        /**
         * Gets the factor str.
         *
         * @return String format with the Factor.
         * @throws EFapsException on error
         */
        public String getFactorStr()
            throws EFapsException
        {
            return useEff ? NumberFormatter.get().getFormatter().format(getFactor()) : "";
        }

        /**
         * @return BigDecimal with the efficiency.
         */
        public BigDecimal getEfficiencyTools()
        {
            return efficiencyTools;
        }

        /**
         * Gets the efficiency tools str.
         *
         * @return String format with the efficiency.
         * @throws EFapsException on error
         */
        public String getEfficiencyToolsStr()
            throws EFapsException
        {
            return useEff ? NumberFormatter.get().getFormatter().format(getEfficiencyTools()) : "";
        }

        /**
         * Gets the efficiency applied.
         *
         * @return the efficiency applied
         */
        public BigDecimal getEfficiencyApplied()
        {
            BigDecimal ret = BigDecimal.ONE;
            if (getPosType() != null) {
                if (getPosType().equals(PositionType.PERSONAL)) {
                    ret = getEfficiencyPersonal().multiply(getEfficiencyOverAll());
                } else if (getPosType().equals(PositionType.TOOLS)) {
                    ret = getEfficiencyTools();
                }
            }
            return ret;
        }

        /**
         * Getter method for the instance variable {@link #posType}.
         *
         * @return value of instance variable {@link #posType}
         */
        protected IPositionType getPosType()
        {
            return posType;
        }

        /**
         * Getter method for the instance variable {@link #uom}.
         *
         * @return value of instance variable {@link #uom}
         */
        protected String getUom()
        {
            return uom;
        }

        /**
         * Getter method for the instance variable {@link #efficiencyOverAll}.
         *
         * @return value of instance variable {@link #efficiencyOverAll}
         */
        public BigDecimal getEfficiencyOverAll()
        {
            return efficiencyOverAll;
        }

        /**
         * Getter method for the instance variable {@link #efficiencyPersonal}.
         *
         * @return value of instance variable {@link #efficiencyPersonal}
         */
        public BigDecimal getEfficiencyPersonal()
        {
            return efficiencyPersonal;
        }

        /**
         * @return base value
         */
        public BigDecimal getBase()
        {
            return getQuantity().multiply(getBaseValue());
        }

        /**
         * @return String format with the efficiency.
         * @throws EFapsException on error
         */
        public String getBaseStr()
            throws EFapsException
        {
            return NumberFormatter.get().getFormatter().format(getBase());
        }

        /**
         * Getter method for the instance variable {@link #baseValue}.
         *
         * @return value of instance variable {@link #baseValue}
         */
        public BigDecimal getBaseValue()
        {
            return baseValue;
        }

        /**
         * Setter method for instance variable {@link #baseValue}.
         *
         * @param _baseValue value for instance variable {@link #baseValue}
         */
        public void setBaseValue(final BigDecimal _baseValue)
        {
            baseValue = _baseValue;
        }

        /**
         * Getter method for the instance variable {@link #remark}.
         *
         * @return value of instance variable {@link #remark}
         */
        public String getRemark()
        {
            return remark;
        }

        /**
         * Setter method for instance variable {@link #remark}.
         *
         * @param _remark value for instance variable {@link #remark}
         */
        public void setRemark(final String _remark)
        {
            remark = _remark;
        }
    }
}
