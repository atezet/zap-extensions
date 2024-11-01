/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2024 The ZAP Development Team
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
package org.zaproxy.zap.extension.sequence.automation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.CommandLine;
import org.parosproxy.paros.Constant;
import org.zaproxy.addon.automation.AutomationData;
import org.zaproxy.addon.automation.AutomationEnvironment;
import org.zaproxy.addon.automation.AutomationJob;
import org.zaproxy.addon.automation.AutomationJobException;
import org.zaproxy.addon.automation.AutomationProgress;
import org.zaproxy.addon.automation.ContextWrapper;
import org.zaproxy.addon.automation.JobResultData;
import org.zaproxy.addon.automation.jobs.ActiveScanJobResultData;
import org.zaproxy.addon.automation.jobs.JobData;
import org.zaproxy.addon.automation.jobs.JobUtils;
import org.zaproxy.addon.automation.jobs.PolicyDefinition;
import org.zaproxy.zap.extension.ascan.ActiveScan;
import org.zaproxy.zap.extension.ascan.ExtensionActiveScan;
import org.zaproxy.zap.extension.ascan.ScanPolicy;
import org.zaproxy.zap.extension.script.ExtensionScript;
import org.zaproxy.zap.extension.script.ScriptWrapper;
import org.zaproxy.zap.extension.sequence.ExtensionSequence;
import org.zaproxy.zap.extension.sequence.StdActiveScanRunner;
import org.zaproxy.zap.extension.zest.ZestScriptWrapper;
import org.zaproxy.zap.model.Target;
import org.zaproxy.zap.users.User;

public class SequenceActiveScanJob extends AutomationJob {

    public static final String JOB_NAME = "sequence-activeScan";

    private static final String RESOURCES_DIR = "/org/zaproxy/zap/extension/sequence/resources/";

    private static final Logger LOGGER = LogManager.getLogger(SequenceActiveScanJob.class);

    private static final String PARAM_CONTEXT = "context";
    private static final String PARAM_POLICY = "policy";
    private static final String PARAM_SEQUENCE = "sequence";
    private static final String PARAM_USER = "user";

    private final ExtensionActiveScan extAScan;
    private final ExtensionScript extScript;

    private Parameters parameters = new Parameters();
    private PolicyDefinition policyDefinition = new PolicyDefinition();
    private Data data;

    public SequenceActiveScanJob(ExtensionActiveScan extAScan, ExtensionScript extScript) {
        this.extAScan = extAScan;
        this.extScript = extScript;
        data = new Data(this, this.parameters, this.policyDefinition);
    }

    @Override
    public AutomationJob newJob() throws AutomationJobException {
        return new SequenceActiveScanJob(extAScan, extScript);
    }

    @Override
    public boolean supportsAlertTests() {
        return true;
    }

    @Override
    public String getKeyAlertTestsResultData() {
        return ActiveScanJobResultData.KEY;
    }

    @Override
    public void verifyParameters(AutomationProgress progress) {
        Map<?, ?> jobData = this.getJobData();
        if (jobData == null) {
            return;
        }

        for (Object key : jobData.keySet().toArray()) {
            switch (key.toString()) {
                case "parameters":
                    LinkedHashMap<?, ?> params = (LinkedHashMap<?, ?>) jobData.get(key);
                    JobUtils.applyParamsToObject(
                            params, this.parameters, this.getName(), null, progress);
                    break;
                case "policyDefinition":
                    // Parse the policy defn
                    PolicyDefinition.parsePolicyDefinition(
                            jobData.get(key), policyDefinition, this.getName(), progress);
                    break;
                case "name":
                case "tests":
                case "type":
                    // Handled before we get here
                    break;
                default:
                    progress.warn(
                            Constant.messages.getString(
                                    "automation.error.element.unknown", this.getName(), key));

                    break;
            }
        }

        this.verifyUser(this.getParameters().getUser(), progress);
    }

    @Override
    public void applyParameters(AutomationProgress progress) {
        JobUtils.applyObjectToObject(
                this.parameters,
                JobUtils.getJobOptions(this, progress),
                this.getName(),
                new String[] {PARAM_SEQUENCE, PARAM_POLICY, PARAM_CONTEXT, PARAM_USER},
                progress,
                getEnv());
    }

    @Override
    public Map<String, String> getCustomConfigParameters() {
        Map<String, String> map = super.getCustomConfigParameters();
        map.put(PARAM_CONTEXT, "");
        return map;
    }

    @Override
    public boolean supportsMonitorTests() {
        return true;
    }

    @Override
    public void runJob(AutomationEnvironment env, AutomationProgress progress) {

        extAScan.setPanelSwitch(false);
        try {

            ContextWrapper context;
            if (StringUtils.isNotEmpty(this.getParameters().getContext())) {
                context = env.getContextWrapper(this.getParameters().getContext());
                if (context == null) {
                    progress.error(
                            Constant.messages.getString(
                                    "sequence.automation.error.context.unknown",
                                    this.getParameters().getContext()));
                    return;
                }
            } else {
                context = env.getDefaultContextWrapper();
            }

            Target target = new Target(context.getContext());
            target.setRecurse(true);
            List<Object> contextSpecificObjects = new ArrayList<>();
            User user = this.getUser(this.getParameters().getUser(), progress);

            ScanPolicy scanPolicy = null;
            if (!StringUtils.isEmpty(this.getParameters().getPolicy())) {
                try {
                    scanPolicy =
                            extAScan.getPolicyManager().getPolicy(this.getParameters().getPolicy());
                } catch (ConfigurationException e) {
                    // Error already raised above
                }
            } else {
                scanPolicy =
                        this.getData()
                                .getPolicyDefinition()
                                .getScanPolicy(this.getName(), progress);
            }
            if (scanPolicy != null) {
                contextSpecificObjects.add(scanPolicy);
            }

            List<ScriptWrapper> scripts = extScript.getScripts(ExtensionSequence.TYPE_SEQUENCE);

            Optional<ScriptWrapper> scriptWrapper =
                    scripts.stream()
                            .filter(s -> s.getName().equals(this.parameters.getSequence()))
                            .findFirst();

            if (scriptWrapper.isEmpty() || !(scriptWrapper.get() instanceof ZestScriptWrapper)) {
                progress.error(
                        Constant.messages.getString(
                                "sequence.automation.error.sequence.unknown",
                                this.getParameters().getSequence()));
                return;
            }

            StdActiveScanRunner zzr =
                    new StdActiveScanRunner(
                            (ZestScriptWrapper) scriptWrapper.get(), scanPolicy, user);

            try {
                zzr.run(null, null);
            } catch (Exception e) {
                progress.error(
                        Constant.messages.getString(
                                "automation.error.unexpected.internal", e.getMessage()));
                LOGGER.error(e.getMessage(), e);
            }
        } finally {
            extAScan.setPanelSwitch(true);
        }
    }

    @Override
    public List<JobResultData> getJobResultData() {
        ActiveScan lastScan = this.extAScan.getLastScan();
        if (lastScan != null) {
            return createJobResultData(lastScan.getId());
        }
        return new ArrayList<>();
    }

    private List<JobResultData> createJobResultData(int scanId) {
        List<JobResultData> list = new ArrayList<>();
        list.add(new ActiveScanJobResultData(this.getName(), this.extAScan.getScan(scanId)));
        return list;
    }

    @Override
    public String getSummary() {
        return Constant.messages.getString(
                "sequence.automation.ascan.summary", this.getParameters().getSequence());
    }

    @Override
    public Data getData() {
        return data;
    }

    @Override
    public Parameters getParameters() {
        return parameters;
    }

    @Override
    public String getType() {
        return JOB_NAME;
    }

    @Override
    public String getTemplateDataMin() {
        return getResourceAsString(this.getType() + "-min.yaml");
    }

    @Override
    public String getTemplateDataMax() {
        return getResourceAsString(this.getType() + "-max.yaml");
    }

    private static String getResourceAsString(String name) {
        try {
            return IOUtils.toString(
                    SequenceActiveScanJob.class.getResourceAsStream(RESOURCES_DIR + name),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            CommandLine.error(
                    Constant.messages.getString(
                            "sequence.automation.error.noresourcefile", RESOURCES_DIR + name));
        }
        return "";
    }

    @Override
    public Order getOrder() {
        return Order.ATTACK;
    }

    @Override
    public Object getParamMethodObject() {
        return null;
    }

    @Override
    public String getParamMethodName() {
        return null;
    }

    @Override
    public void showDialog() {
        // TODO Implement in a future PR
    }

    @Getter
    public static class Data extends JobData {
        private final Parameters parameters;
        private final PolicyDefinition policyDefinition;

        public Data(AutomationJob job, Parameters parameters, PolicyDefinition policyDefinition) {
            super(job);
            this.parameters = parameters;
            this.policyDefinition = policyDefinition;
        }
    }

    @Getter
    @Setter
    public static class Parameters extends AutomationData {
        private String sequence = "";
        private String context = "";
        private String user = "";
        private String policy = "";
    }
}
