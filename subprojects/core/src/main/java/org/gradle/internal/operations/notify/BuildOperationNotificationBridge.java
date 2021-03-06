/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.operations.notify;

import org.gradle.api.execution.internal.ExecuteTaskBuildOperationDetails;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.BuildOperationListenerManager;
import org.gradle.internal.progress.OperationFinishEvent;
import org.gradle.internal.progress.OperationStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class BuildOperationNotificationBridge implements BuildOperationNotificationListenerRegistrar, Stoppable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildOperationNotificationBridge.class);

    private Listener operationListener;
    private final BuildOperationListenerManager buildOperationListenerManager;

    BuildOperationNotificationBridge(BuildOperationListenerManager buildOperationListenerManager) {
        this.buildOperationListenerManager = buildOperationListenerManager;
    }

    @Override
    public void registerBuildScopeListener(BuildOperationNotificationListener notificationListener) {
        if (operationListener == null) {
            operationListener = new Listener(notificationListener);
            buildOperationListenerManager.addListener(operationListener);
        } else {
            throw new IllegalStateException("listener is already registered");
        }
    }

    @Override
    public void stop() {
        if (operationListener != null) {
            buildOperationListenerManager.removeListener(operationListener);
        }
    }

    /*
        Note: the intention here is to work towards not having to create new objects
        to meet the notification object interfaces.
        Instead, the base types like BuildOperationDescriptor should implement them natively.
        However, this will require restructuring this type and associated things such as
        OperationStartEvent. This will happen later.
     */

    private static class Listener implements BuildOperationListener {

        private final BuildOperationNotificationListener notificationListener;

        private final Map<Object, Object> parents = new ConcurrentHashMap<Object, Object>();
        private final Map<Object, Object> active = new ConcurrentHashMap<Object, Object>();

        private Listener(BuildOperationNotificationListener notificationListener) {
            this.notificationListener = notificationListener;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            Object id = buildOperation.getId();
            Object parentId = buildOperation.getParentId();
            if (parentId != null) {
                if (active.containsKey(parentId)) {
                    parents.put(id, parentId);
                } else {
                    parentId = parents.get(parentId);
                    if (parentId != null) {
                        parents.put(id, parentId);
                    }
                }
            }

            if (!isNotificationWorthy(buildOperation)) {
                return;
            }

            active.put(id, "");

            Started notification = new Started(id, parentId, buildOperation.getDetails());
            try {
                notificationListener.started(notification);
            } catch (Exception e) {
                LOGGER.debug("Build operation notification listener threw an error on " + notification, e);
            }
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            Object id = buildOperation.getId();
            parents.remove(id);
            if (active.remove(id) == null) {
                return;
            }

            Finished notification = new Finished(id, buildOperation.getDetails(), finishEvent.getResult(), finishEvent.getFailure());
            try {
                notificationListener.finished(notification);
            } catch (Exception e) {
                LOGGER.debug("Build operation notification listener threw an error on " + notification, e);
            }
        }
    }

    private static boolean isNotificationWorthy(BuildOperationDescriptor buildOperation) {
        // replace this with opt-in to exposing on producer side
        // it just so happens right now that this is a reasonable heuristic
        Object details = buildOperation.getDetails();
        return details != null && !(details instanceof ExecuteTaskBuildOperationDetails);
    }

    private static class Started implements BuildOperationStartedNotification {

        private final Object id;
        private final Object parentId;
        private final Object details;

        private Started(Object id, Object parentId, Object details) {
            this.id = id;
            this.parentId = parentId;
            this.details = details;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Override
        public Object getNotificationOperationParentId() {
            return parentId;
        }

        @Override
        public Object getNotificationOperationDetails() {
            return details;
        }

        @Override
        public String toString() {
            return "BuildOperationStartedNotification{"
                + "id=" + id
                + ", parentId=" + parentId
                + ", details=" + details
                + '}';
        }
    }

    private static class Finished implements BuildOperationFinishedNotification {

        private final Object id;
        private final Object details;
        private final Object result;
        private final Throwable failure;

        private Finished(Object id, Object details, Object result, Throwable failure) {
            this.id = id;
            this.details = details;
            this.result = result;
            this.failure = failure;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Override
        public Object getNotificationOperationDetails() {
            return details;
        }

        @Override
        public Object getNotificationOperationResult() {
            return result;
        }

        @Override
        public Throwable getNotificationOperationFailure() {
            return failure;
        }

        @Override
        public String toString() {
            return "BuildOperationFinishedNotification{"
                + "id=" + id
                + ", details=" + details
                + ", result=" + result
                + ", failure=" + failure
                + '}';
        }
    }

}
