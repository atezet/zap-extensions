/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2022 The ZAP Development Team
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
package org.zaproxy.addon.spider.automation;

import java.util.List;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.extension.Extension;
import org.parosproxy.paros.extension.ExtensionAdaptor;
import org.parosproxy.paros.extension.ExtensionHook;
import org.zaproxy.addon.automation.ExtensionAutomation;
import org.zaproxy.addon.spider.ExtensionSpider2;

public class ExtensionSpiderAutomation extends ExtensionAdaptor {

    private static final List<Class<? extends Extension>> DEPENDENCIES =
            List.of(ExtensionSpider2.class, ExtensionAutomation.class);

    private SpiderJob job;

    @Override
    public String getUIName() {
        return Constant.messages.getString("spider.automation.name");
    }

    @Override
    public String getDescription() {
        return Constant.messages.getString("spider.automation.desc");
    }

    @Override
    public List<Class<? extends Extension>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public void hook(ExtensionHook extensionHook) {
        job = new SpiderJob();
        getExtension(ExtensionAutomation.class).registerAutomationJob(job);
    }

    private static <T extends Extension> T getExtension(Class<T> clazz) {
        return Control.getSingleton().getExtensionLoader().getExtension(clazz);
    }

    @Override
    public boolean canUnload() {
        return true;
    }

    @Override
    public void unload() {
        getExtension(ExtensionAutomation.class).unregisterAutomationJob(job);
    }
}
