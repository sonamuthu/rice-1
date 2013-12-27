/**
 * Copyright 2005-2013 The Kuali Foundation
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
package org.kuali.rice.krad.uif.lifecycle;

import java.util.LinkedList;
import java.util.Queue;

import org.kuali.rice.krad.datadictionary.Copyable;
import org.kuali.rice.krad.uif.component.Component;
import org.kuali.rice.krad.uif.lifecycle.ViewLifecycle.LifecycleEvent;
import org.kuali.rice.krad.uif.util.LifecycleElement;
import org.kuali.rice.krad.uif.util.ObjectPropertyUtils;
import org.kuali.rice.krad.uif.util.ProcessLogger;
import org.kuali.rice.krad.uif.view.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Base abstract implementation for a lifecycle phase.
 * 
 * @author Kuali Rice Team (rice.collab@kuali.org)
 */
public abstract class ViewLifecyclePhaseBase implements ViewLifecyclePhase {

    private final Logger LOG = LoggerFactory.getLogger(ViewLifecyclePhaseBase.class);

    private LifecycleElement element;
    private Object model;
    private Component parent;
    private String path;
    private ViewLifecyclePhaseBase predecessor;

    private ViewLifecyclePhase nextPhase;
    
    private boolean processed;
    private boolean completed;
  
    private int pendingSuccessors = -1;

    private ViewLifecycleTask currentTask;

    /**
     * Resets this phase for recycling.
     */
    protected void recycle() {
        trace("recycle");
        element = null;
        model = null;
        path = null;
        predecessor = null;
        nextPhase = null;
        processed = false;
        completed = false;
        pendingSuccessors = -1;
    }

    /**
     * Prepares this phase for reuse.
     * 
     * @param element The element to be processed by this phase.
     * @param model The model associated with the lifecycle at this phase.
     * @param index The position within the predecessor phase's successor list of this phase.
     * @param parent The parent element. For top-down phases, this component will be associated
     *        with the predecessor phase. For bottom-up phases (rendering), this element will be
     *        associated with a successor phases.
     * @param nextPhase The lifecycle phase to queue directly upon completion of this phase, if
     *        applicable.
     *        
     * @see LifecyclePhaseFactory
     */
    protected void prepare(LifecycleElement element, Object model,
            String path, Component parent, ViewLifecyclePhase nextPhase) {
        if (element.getViewStatus().equals(getEndViewStatus())) {
            ViewLifecycle.reportIllegalState(
                    "Component is already in the expected end status " + getEndViewStatus()
                            + " before this phase " + element.getClass() + " " + element.getId());
        }

        this.model = model;
        this.path = path;
        this.element = element;
        this.parent = parent;
        this.nextPhase = nextPhase;

        trace("prepare");
    }

    /**
     * Initializes queue of pending tasks phases.
     * 
     * <p>
     * This method will be called before during processing to determine which tasks to perform at
     * this phase.
     * </p>
     * 
     * @param tasks The queue of tasks to perform.
     */
    protected abstract void initializePendingTasks(Queue<ViewLifecycleTask> tasks);

    /**
     * Initializes queue of successor phases.
     * 
     * <p>
     * This method will be called while processing this phase after all tasks have been performed,
     * to determine phases to queue for successor processing. This phase will not be considered
     * complete until all successors queued by this method, and all subsequent successor phases,
     * have completed processing.
     * </p>
     * 
     * @param successors The queue of successor phases.
     */
    protected abstract void initializeSuccessors(Queue<ViewLifecyclePhase> successors);

    /**
     * {@inheritDoc}
     */
    @Override
    public final LifecycleElement getElement() {
        return element;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Object getModel() {
        return model;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Component getParent() {
        return this.parent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPath() {
        return this.path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isProcessed() {
        return processed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isComplete() {
        return completed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ViewLifecyclePhase getPredecessor() {
        return predecessor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ViewLifecycleTask getCurrentTask() {
        return this.currentTask;
    }

    /**
     * Validates this phase and thread state before processing and logs activity.
     * @see #run()
     */
    private void validateBeforeProcessing() {
        if (processed) {
            throw new IllegalStateException(
                    "Lifecycle phase has already been processed " + this);
        }

        if (predecessor != null && !predecessor.isProcessed()) {
            throw new IllegalStateException(
                    "Predecessor phase has not completely processed " + this);
        }

        if (!ViewLifecycle.isActive()) {
            throw new IllegalStateException("No view lifecyle is not active on the current thread");
        }

        if (LOG.isDebugEnabled()) {
            trace("ready " + getStartViewStatus() + " -> " + getEndViewStatus());
        }
    }

    /**
     * Executes the lifecycle phase.
     * 
     * <p>
     * This method performs state validation and updates component view status. Use
     * {@link #initializePendingTasks(Queue)} to provide phase-specific behavior.
     * </p>
     * 
     * @see java.lang.Runnable#run()
     */
    @Override
    public final void run() {
        try {
            ViewLifecycleProcessorBase processor =
                    (ViewLifecycleProcessorBase) ViewLifecycle.getProcessor();

            validateBeforeProcessing();

            try {
                if (ViewLifecycle.isTrace() && ProcessLogger.isTraceActive()) {
                    ProcessLogger.ntrace("lc-" + getStartViewStatus() + "-" + getEndViewStatus() + ":", ":"
                            + getElement().getClass().getSimpleName(), 1000);
                    ProcessLogger.countBegin("lc-" + getStartViewStatus() + "-" + getEndViewStatus());
                }

                processor.setActivePhase(this);

                if (!element.getViewStatus().equals(getStartViewStatus())) {
                    ViewLifecycle.reportIllegalState(
                            "Component is not in the expected status " + getStartViewStatus()
                                    + " at the start of this phase, found " + element.getClass()
                                    + " " + element.getId() + " " + element.getViewStatus() +
                                    "\nThis phase: " + this);
                }

                trace("path-update " + element.getPath());
                View view = ViewLifecycle.getView();
                
                if (ViewLifecycle.isStrict()) {
                    if (element == view) {
                        if (!StringUtils.isEmpty(path)) {
                            ViewLifecycle.reportIllegalState("View path is not empty " + path);
                        }
                    } else {
                        LifecycleElement referredElement = (LifecycleElement)
                                ObjectPropertyUtils.getPropertyValue(view, path);
                        if (referredElement != null) {
                            referredElement = (LifecycleElement) referredElement.unwrap();
                            if (element != referredElement) {
                                ViewLifecycle.reportIllegalState("Path " + path
                                        + " refers to an element other than " + element.getClass()
                                        + " " + element.getId() + " " + element.getPath()
                                        + (referredElement == null ? "" : " " + referredElement.getClass()
                                                + " " + referredElement.getId() + " " + referredElement.getPath()));
                            }
                        }
                    }
                }
                element.setPath(getPath());
                
                Queue<ViewLifecycleTask> pendingTasks = new LinkedList<ViewLifecycleTask>();
                initializePendingTasks(pendingTasks);

                while (!pendingTasks.isEmpty()) {
                    ViewLifecycleTask task = pendingTasks.poll();
                    currentTask = task;
                    task.run();
                    currentTask = null;
                }

                element.setViewStatus(this);
                processed = true;

            } finally {
                processor.setActivePhase(null);

                if (ViewLifecycle.isTrace() && ProcessLogger.isTraceActive()) {
                    ProcessLogger.countEnd("lc-" + getStartViewStatus() + "-" + getEndViewStatus(), getElement()
                            .getClass() + " " + getElement().getId());
                }
            }

            assert pendingSuccessors == -1 : this;

            Queue<ViewLifecyclePhase> successors = new LinkedList<ViewLifecyclePhase>();
            initializeSuccessors(successors);
            pendingSuccessors = successors.size();
            trace("processed " + pendingSuccessors);

            if (pendingSuccessors == 0) {
                notifyCompleted();
            } else {
                for (ViewLifecyclePhase successor : successors) {
                    if (successor instanceof ViewLifecyclePhaseBase) {
                        ViewLifecyclePhaseBase successorBase = (ViewLifecyclePhaseBase) successor;
                        assert successorBase.predecessor == null : this + " " + successorBase;
                        successorBase.predecessor = this;
                        successorBase.trace("succ-pend");
                    }

                    ViewLifecycle.getProcessor().offerPendingPhase(successor);
                }
            }

        } catch (Throwable t) {
            trace("error");
            LOG.warn("Error in lifecycle phase " + this, t);

            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                throw new IllegalStateException("Unexpected error in lifecycle phase " + this, t);
            }
        }
    }

    /**
     * Notifies predecessors that this task has completed.
     */
    protected final void notifyCompleted() {
        trace("complete");
        assert !completed : this;
        completed = true;
        
        LifecycleEvent event = getEventToNotify();
        if (event != null) {
            ViewLifecycle.getActiveLifecycle().invokeEventListeners(
                    event, ViewLifecycle.getView(), ViewLifecycle.getModel(), element);
        }

        element.notifyCompleted(this);

        if (nextPhase != null) {
            ViewLifecycle.getProcessor().pushPendingPhase(nextPhase);
        }
        
        synchronized (this) {
            if (predecessor != null) {
                synchronized (predecessor) {
                    predecessor.pendingSuccessors--;
                    if (predecessor.pendingSuccessors == 0) {
                        predecessor.notifyCompleted();
                    }
                    LifecyclePhaseFactory.recycle(this);
                }
            } else {
                trace("notify");
                notifyAll();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Queue<ViewLifecyclePhase> toPrint = new LinkedList<ViewLifecyclePhase>();
        toPrint.offer(this);
        while (!toPrint.isEmpty()) {
            ViewLifecyclePhase tp = toPrint.poll();

            if (tp.getModel() == null || tp.getElement() == null) {
                sb.append("\n      ");
                sb.append(tp.getClass().getSimpleName());
                sb.append(" (recycled)");
                continue;
            }

            String indent;
            if (tp == this) {
                sb.append("Model: ");
                sb.append(this.model.getClass().getSimpleName());
                sb.append("\nProcessed? ");
                sb.append(processed);
                indent = "\n";
            } else {
                indent = "\n    ";
            }
            sb.append(indent);

            sb.append(tp.getClass().getSimpleName());
            sb.append(" ");
            sb.append(System.identityHashCode(tp));
            sb.append(" ");
            sb.append(tp.getPath());
            sb.append(" ");
            sb.append(tp.getElement().getClass().getSimpleName());
            sb.append(" ");
            sb.append(tp.getElement().getId());
            sb.append(" ");
            sb.append(pendingSuccessors);

            if (tp == this) {
                sb.append("\nPredecessor Phases:");
            }

            ViewLifecyclePhase tpredecessor = tp.getPredecessor();
            if (tpredecessor != null) {
                toPrint.add(tpredecessor);
            }
        }
        return sb.toString();
    }

    /**
     * Logs a trace message related to processing this lifecycle, when tracing is active and
     * debugging is enabled.
     * 
     * @param step The step in processing the phase that has been reached.
     * @see ViewLifecycle#isTrace()
     */
    private void trace(String step) {
        if (ViewLifecycle.isTrace() && LOG.isDebugEnabled()) {
            String msg = System.identityHashCode(this) + " " + getClass() + " " + step + " " + path + " " +
                    (element == null ? "(recycled)" : element.getClass() + " " + element.getId());
            LOG.debug(msg);
        }
    }

}
