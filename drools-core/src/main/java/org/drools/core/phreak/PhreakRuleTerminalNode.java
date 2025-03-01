/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.drools.core.phreak;

import org.drools.core.common.ActivationsManager;
import org.drools.core.common.AgendaItem;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.common.PropagationContext;
import org.drools.core.rule.accessor.Salience;
import org.drools.core.reteoo.Tuple;
import org.kie.api.event.rule.MatchCancelledCause;

/**
* Created with IntelliJ IDEA.
* User: mdproctor
* Date: 03/05/2013
* Time: 15:42
* To change this template use File | Settings | File Templates.
*/
public class PhreakRuleTerminalNode {
    public void doNode(TerminalNode rtnNode,
                       ActivationsManager activationsManager,
                       TupleSets<LeftTuple> srcLeftTuples,
                       RuleExecutor executor) {
        if (srcLeftTuples.getDeleteFirst() != null) {
            doLeftDeletes(activationsManager, srcLeftTuples, executor);
        }

        if (srcLeftTuples.getUpdateFirst() != null) {
            doLeftUpdates(rtnNode, activationsManager, srcLeftTuples, executor);
        }

        if (srcLeftTuples.getInsertFirst() != null) {
            doLeftInserts(rtnNode, activationsManager, srcLeftTuples, executor);
        }

        srcLeftTuples.resetAll();
    }

    public void doLeftInserts(TerminalNode rtnNode,
                              ActivationsManager activationsManager,
                              TupleSets<LeftTuple> srcLeftTuples,
                              RuleExecutor executor) {
        RuleAgendaItem ruleAgendaItem = executor.getRuleAgendaItem();

        if ( rtnNode.getRule().getAutoFocus() && !ruleAgendaItem.getAgendaGroup().isActive() ) {
            activationsManager.getAgendaGroupsManager().setFocus( ruleAgendaItem.getAgendaGroup() );
        }

        for (LeftTuple leftTuple = srcLeftTuples.getInsertFirst(); leftTuple != null; ) {
            LeftTuple next = leftTuple.getStagedNext();

            doLeftTupleInsert(rtnNode, executor, activationsManager, ruleAgendaItem, leftTuple);

            leftTuple.clearStaged();
            leftTuple = next;
        }
    }

    public static void doLeftTupleInsert(TerminalNode rtnNode, RuleExecutor executor,
                                         ActivationsManager activationsManager, RuleAgendaItem ruleAgendaItem,
                                         LeftTuple leftTuple) {
        ReteEvaluator reteEvaluator = activationsManager.getReteEvaluator();
        if ( reteEvaluator.getSessionConfiguration().isDirectFiring() ) {
            executor.addLeftTuple(leftTuple);
            return;
        }

        PropagationContext pctx = leftTuple.findMostRecentPropagationContext();

        if ( rtnNode.getRule().isNoLoop() && rtnNode.equals(pctx.getTerminalNodeOrigin()) ) {
            return;
        }

        int salienceInt = getSalienceValue( rtnNode, ruleAgendaItem, ( AgendaItem ) leftTuple, reteEvaluator );

        RuleTerminalNodeLeftTuple rtnLeftTuple = (RuleTerminalNodeLeftTuple) leftTuple;
        activationsManager.createAgendaItem( rtnLeftTuple, salienceInt, pctx, ruleAgendaItem, ruleAgendaItem.getAgendaGroup() );

        activationsManager.getAgendaEventSupport().fireActivationCreated(rtnLeftTuple, activationsManager.getReteEvaluator());

        if (  rtnNode.getRule().isLockOnActive() &&
              leftTuple.getPropagationContext().getType() != PropagationContext.Type.RULE_ADDITION ) {
            InternalAgendaGroup agendaGroup = executor.getRuleAgendaItem().getAgendaGroup();
            if (blockedByLockOnActive(rtnNode.getRule(), pctx, agendaGroup)) {
                activationsManager.getAgendaEventSupport().fireActivationCancelled(rtnLeftTuple, reteEvaluator, MatchCancelledCause.FILTER );
                return;
            }
        }

        if (activationsManager.getActivationsFilter() != null && !activationsManager.getActivationsFilter().accept( rtnLeftTuple, reteEvaluator, rtnNode)) {
            // only relevant for seralization, to not refire Matches already fired
            return;
        }

        executor.addLeftTuple( leftTuple );
        leftTuple.increaseActivationCountForEvents(); // increased here, decreased in Agenda's cancelActivation and fireActivation

        activationsManager.addItemToActivationGroup( rtnLeftTuple );
        if ( !rtnNode.isFireDirect() && executor.isDeclarativeAgendaEnabled() ) {
            insertAndStageActivation( reteEvaluator, rtnLeftTuple );
        }
    }

    private static void insertAndStageActivation(ReteEvaluator reteEvaluator, AgendaItem activation) {
        ObjectTypeConf activationObjectTypeConf = reteEvaluator.getDefaultEntryPoint().getObjectTypeConfigurationRegistry().getObjectTypeConf(activation );
        InternalFactHandle factHandle = reteEvaluator.getFactHandleFactory().newFactHandle( activation, activationObjectTypeConf, reteEvaluator, reteEvaluator.getDefaultEntryPoint() );
        reteEvaluator.getDefaultEntryPoint().getEntryPointNode().assertActivation( factHandle, activation.getPropagationContext(), reteEvaluator );
        activation.setActivationFactHandle( factHandle );
    }

    private static int getSalienceValue( TerminalNode rtnNode, RuleAgendaItem ruleAgendaItem, AgendaItem leftTuple, ReteEvaluator reteEvaluator ) {
        Salience salience = ruleAgendaItem.getRule().getSalience();
        return salience == null ? 0 : (salience.isDynamic() ?
                    salience.getValue(leftTuple, rtnNode.getRule(), reteEvaluator) :
                    salience.getValue() );
    }

    public void doLeftUpdates(TerminalNode rtnNode,
                              ActivationsManager activationsManager,
                              TupleSets<LeftTuple> srcLeftTuples,
                              RuleExecutor executor) {
        RuleAgendaItem ruleAgendaItem = executor.getRuleAgendaItem();
        if ( rtnNode.getRule().getAutoFocus() && !ruleAgendaItem.getAgendaGroup().isActive() ) {
            activationsManager.getAgendaGroupsManager().setFocus(ruleAgendaItem.getAgendaGroup());
        }

        for (LeftTuple leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; ) {
            LeftTuple next = leftTuple.getStagedNext();

            doLeftTupleUpdate(rtnNode, executor, activationsManager, leftTuple);

            leftTuple.clearStaged();
            leftTuple = next;
        }
    }

    public static void doLeftTupleUpdate(TerminalNode rtnNode, RuleExecutor executor,
                                         ActivationsManager activationsManager, LeftTuple leftTuple) {
        RuleTerminalNodeLeftTuple rtnLeftTuple = (RuleTerminalNodeLeftTuple) leftTuple;
        ReteEvaluator reteEvaluator = activationsManager.getReteEvaluator();

        if ( reteEvaluator.getSessionConfiguration().isDirectFiring() ) {
            if (!rtnLeftTuple.isQueued() ) {
                executor.addLeftTuple( leftTuple );
                reteEvaluator.getRuleEventSupport().onUpdateMatch( rtnLeftTuple );
            }
            return;
        }

        PropagationContext pctx = leftTuple.findMostRecentPropagationContext();

        boolean blocked = false;
        if( executor.isDeclarativeAgendaEnabled() ) {
           if ( rtnLeftTuple.hasBlockers() ) {
               blocked = true; // declarativeAgenda still blocking LeftTuple, so don't add back ot list
           }
        } else {
            blocked = rtnNode.getRule().isNoLoop() && rtnNode.equals(pctx.getTerminalNodeOrigin());
        }

        int salienceInt = getSalienceValue( rtnNode, executor.getRuleAgendaItem(), ( AgendaItem ) leftTuple, reteEvaluator );
        
        if (activationsManager.getActivationsFilter() != null && !activationsManager.getActivationsFilter().accept( rtnLeftTuple, reteEvaluator, rtnNode)) {
            // only relevant for serialization, to not re-fire Matches already fired
            return;
        }
        
        if ( !blocked ) {
            boolean addToExector = true;
            if (  rtnNode.getRule().isLockOnActive() &&
                  pctx.getType() != PropagationContext.Type.RULE_ADDITION ) {

                InternalAgendaGroup agendaGroup = executor.getRuleAgendaItem().getAgendaGroup();
                if (blockedByLockOnActive(rtnNode.getRule(), pctx, agendaGroup)) {
                    addToExector = false;
                }
            }
            if ( addToExector ) {
                if (!rtnLeftTuple.isQueued() ) {
                    // not queued, so already fired, so it's effectively recreated
                    activationsManager.getAgendaEventSupport().fireActivationCreated( rtnLeftTuple, reteEvaluator );

                    rtnLeftTuple.update( salienceInt, pctx );
                    executor.addLeftTuple( leftTuple );
                    reteEvaluator.getRuleEventSupport().onUpdateMatch( rtnLeftTuple );
                }
            }

        } else {
            // LeftTuple is blocked, and thus not queued, so just update it's values
            rtnLeftTuple.update(salienceInt, pctx);
        }

        if( !rtnNode.isFireDirect() && executor.isDeclarativeAgendaEnabled()) {
            modifyActivation(reteEvaluator, rtnLeftTuple);
        }
    }

    private static void modifyActivation(ReteEvaluator reteEvaluator, AgendaItem activation) {
        // in Phreak this is only called for declarative agenda, on rule instances
        InternalFactHandle factHandle = activation.getActivationFactHandle();
        if ( factHandle != null ) {
            // removes the declarative rule instance for the real rule instance
            reteEvaluator.getDefaultEntryPoint().getEntryPointNode().modifyActivation( factHandle, activation.getPropagationContext(), reteEvaluator );
        }
    }

    public void doLeftDeletes(ActivationsManager activationsManager,
                              TupleSets<LeftTuple> srcLeftTuples,
                              RuleExecutor executor) {

        for (LeftTuple leftTuple = srcLeftTuples.getDeleteFirst(); leftTuple != null; ) {
            LeftTuple next = leftTuple.getStagedNext();
            doLeftDelete(activationsManager, executor, leftTuple);

            leftTuple.clearStaged();
            leftTuple = next;
        }
    }

    public static void doLeftDelete(ActivationsManager activationsManager, RuleExecutor executor, Tuple leftTuple) {
        RuleTerminalNodeLeftTuple rtnLt = ( RuleTerminalNodeLeftTuple ) leftTuple;
        rtnLt.setMatched( false );

        rtnLt.cancelActivation( activationsManager );

        if ( leftTuple.getMemory() != null ) {
            // Expiration propagations should not be removed from the list, as they still need to fire
            executor.removeLeftTuple(leftTuple);
        }

        leftTuple.setContextObject( null );
    }

    private static boolean blockedByLockOnActive(RuleImpl rule,
                                          PropagationContext pctx,
                                          InternalAgendaGroup agendaGroup) {
        if ( rule.isLockOnActive() ) {
            long handleRecency = pctx.getFactHandle().getRecency();
            boolean isActive = agendaGroup.isActive();
            long activatedForRecency = agendaGroup.getActivatedForRecency();
            long clearedForRecency = agendaGroup.getClearedForRecency();

            if ( isActive && activatedForRecency < handleRecency &&
                 agendaGroup.getAutoFocusActivator() != pctx ) {
                return true;
            } else {
                return clearedForRecency != -1 && clearedForRecency >= handleRecency;
            }

        }
        return false;
    }
}
