/*
 * VariableLayoutValueCellModifier.java
 * Created on May 22, 2007
 *
 * Copyright 2007 Wind River Systems Inc. All rights reserved.
*/
package org.eclipse.dd.dsf.debug.ui.viewmodel.variable;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.dd.dsf.debug.service.IFormattedValues;
import org.eclipse.dd.dsf.debug.service.IExpressions.IExpressionDMContext;
import org.eclipse.dd.dsf.debug.service.IExpressions.IExpressionDMData;
import org.eclipse.dd.dsf.debug.ui.viewmodel.IDebugVMConstants;
import org.eclipse.dd.dsf.debug.ui.viewmodel.expression.WatchExpressionCellModifier;
import org.eclipse.dd.dsf.debug.ui.viewmodel.numberformat.IFormattedValuePreferenceStore;
import org.eclipse.dd.dsf.ui.viewmodel.IVMContext;
import org.eclipse.dd.dsf.ui.viewmodel.update.AbstractCachingVMProvider;
import org.eclipse.dd.dsf.ui.viewmodel.update.UserEditEvent;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IPresentationContext;

@SuppressWarnings("restriction")
public class VariableCellModifier extends WatchExpressionCellModifier {
    
    private AbstractCachingVMProvider fProvider;
    private SyncVariableDataAccess fDataAccess = null;
    private IFormattedValuePreferenceStore fPrefStore;
    
    public VariableCellModifier(AbstractCachingVMProvider provider, 
        IFormattedValuePreferenceStore formattedValuePreferenceStore, SyncVariableDataAccess access) 
    {
        fProvider = provider;
        fDataAccess = access;
        fPrefStore = formattedValuePreferenceStore;
    }
    
    /*
     *  Used to make sure we are dealing with a valid variable.
     */
    private IExpressionDMContext getVariableDMC(Object element) {
        if (element instanceof IAdaptable) {
            return (IExpressionDMContext)((IAdaptable)element).getAdapter(IExpressionDMContext.class);
        }
        return null;
    }

    @Override
    public boolean canModify(Object element, String property) {
        // If we're in the column value, modify the register data.  Otherwise, call the super-class to edit
        // the watch expression.

        if (IDebugVMConstants.COLUMN_ID__VALUE.equals(property)) { 
            // Make sure we are are dealing with a valid set of information.
                
            if (getVariableDMC(element) == null)
                return false;
            
           IExpressionDMData exprData = fDataAccess.readVariable(element);
            
            if ( ( exprData != null ) && ( ! exprData.isEditable() ) ) return false;
 
            return true ;
        }

        return super.canModify(element, property);
    }

    @Override
    public Object getValue(Object element, String property) {
        // If we're in the column value, modify the variable value.  Otherwise, call the super-class to edit
        // the watch expression.

        if (IDebugVMConstants.COLUMN_ID__VALUE.equals(property)) {
            /*
             *  We let the Model provider supply the current format.
             */
            String formatId;
            
            if ( element instanceof IVMContext) {
                /*
                 *  Find the presentation context and then use it to get the current desired format.
                 */
                IVMContext ctx = (IVMContext) element;
                IPresentationContext presCtx = ctx.getVMNode().getVMProvider().getPresentationContext();
                
                formatId = fPrefStore.getCurrentNumericFormat(presCtx);
            }
            else {
                formatId = IFormattedValues.NATURAL_FORMAT;
            }
            
            String value = fDataAccess.getFormattedValue(element, formatId);
            
            if (value == null) {
                return "...";  //$NON-NLS-1$
            }

            return value;
        }

        return super.getValue(element, property);
    }

    @Override
    public void modify(Object element, String property, Object value) {
        /* 
         * If we're in the column value, modify the register data.  Otherwise, call the super-class to edit
         * the watch expression.
         */ 
        if (IDebugVMConstants.COLUMN_ID__VALUE.equals(property)) {
            if (value instanceof String) {
                /*
                 *  We let the Model provider supply the current format.
                 */
                String formatId;
                
                if ( element instanceof IVMContext) {
                    /*
                     *  Find the presentation context and then use it to get the current desired format.
                     */
                    IVMContext ctx = (IVMContext) element;
                    IPresentationContext presCtx = ctx.getVMNode().getVMProvider().getPresentationContext();
                    
                    formatId = fPrefStore.getCurrentNumericFormat(presCtx);
                }
                else {
                    formatId = IFormattedValues.NATURAL_FORMAT;
                }
                
                fProvider.handleEvent(new UserEditEvent(element));
            }
        }
        else {
            super.modify(element, property, value);
        }
    }

}
