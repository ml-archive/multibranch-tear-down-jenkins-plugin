//
//  JobTearDownListener.java
//
//  Copyright (c) 2018 Fuzz Productions, LLC (http://fuzzproductions.com/)
//
//  Permission is hereby granted, free of charge, to any person obtaining a copy
//  of this software and associated documentation files (the "Software"), to deal
//  in the Software without restriction, including without limitation the rights
//  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//  copies of the Software, and to permit persons to whom the Software is
//  furnished to do so, subject to the following conditions:
//
//  The above copyright notice and this permission notice shall be included in
//  all copies or substantial portions of the Software.
//
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//  THE SOFTWARE.
//

package com.fuzzpro.multibranchteardown;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.model.listeners.ItemListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.MultiBranchProject;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import java.util.logging.Logger;

@Extension
public class JobTearDownListener extends ItemListener {

    static final String LOGGER = JobTearDownListener.class.getSimpleName();

    @Override
    public void onUpdated(Item item) {
        Logger.getLogger(LOGGER).fine("Job Class: " + item.getClass().getSimpleName());
        if (canTearDownInfrastructure(item)) {
            Job job = (Job) item;
            String branchName = getBranch(item);
            String remoteUrl = getRemote(item);
            AbstractProject tearDownJob = getTearDownJob(job);
            Logger.getLogger(LOGGER).fine(String.format("Job Info: %s %s %s", item.getFullDisplayName(), branchName, remoteUrl));
            if (tearDownJob != null && branchName != null && remoteUrl != null) {
                Logger.getLogger(LOGGER).fine(String.format("Execute Job: %s %s", branchName, remoteUrl));
                Cause cause = new Cause.UpstreamCause(job.getLastBuild());
                StringParameterValue git_url = new StringParameterValue("git_url", remoteUrl);
                StringParameterValue branch_name = new StringParameterValue("branch_name", branchName);
                ParametersAction paramsAction = new ParametersAction(git_url, branch_name);
                tearDownJob.scheduleBuild2(0, cause, paramsAction);
            }
            //TODO should we allow other project types for tear-down
        }
    }

    private AbstractProject getTearDownJob(Job job) {
        Item tearDownJob;
        JobProperty prop = job.getProperty(JobTearDownProperty.class);
        JobTearDownConfiguration config = GlobalConfiguration.all().get(JobTearDownConfiguration.class);
        String jobName = null;
        if (config != null) {
            jobName = config.getTearDownJob();
        }
        if (prop != null && prop instanceof JobTearDownProperty) {
            JobTearDownProperty jtdprop = (JobTearDownProperty) prop;
            Logger.getLogger(LOGGER).fine("Execute tear down on: " + jtdprop.getJobName());
            tearDownJob = Jenkins.get().getItemByFullName(jtdprop.getJobName());
        }else if (jobName != null && !jobName.trim().isEmpty()) {
            Logger.getLogger(LOGGER).fine("Default Job: " + config.getTearDownJob());
            tearDownJob = Jenkins.get().getItemByFullName(jobName);
        } else {
            tearDownJob = Jenkins.get().getItemByFullName("job-tear-down-executor");
        }
        if (tearDownJob instanceof AbstractProject) {
            return (AbstractProject) tearDownJob;
        }
        return null;
    }

    private boolean canTearDownInfrastructure(Item item) {
        boolean isDisabled = false;
        if (item instanceof AbstractProject) {
            isDisabled = ((AbstractProject) item).isDisabled();
        } else if (item instanceof WorkflowJob) {
            isDisabled = ((WorkflowJob) item).isDisabled();
        }
        return item instanceof Job && isDisabled;
    }

    private String getBranch(Item item) {
        String branchName = null;
        WorkflowJob job = (WorkflowJob) item;
        ItemGroup parent = item.getParent();
        if (parent instanceof MultiBranchProject) {
            BranchProjectFactory projectFactory = ((MultiBranchProject) parent).getProjectFactory();
            if (projectFactory.isProject(item)) {
                Branch branch = projectFactory.getBranch(job);
                SCMHead head = branch.getHead();
                branchName = branch.getName();
                if (head instanceof ChangeRequestSCMHead2) {
                    branchName = ((ChangeRequestSCMHead2) head).getOriginName();
                }
            }
        }
        //TODO add support for other types of project
        return branchName;
    }

    private String getRemote(Item item) {
        String remoteURL = null;
        SCM scm = null;
        if (item instanceof WorkflowJob) {
            WorkflowJob job = (WorkflowJob) item;
            scm = job.getTypicalSCM();
        } else if(item instanceof AbstractProject) {
            scm = ((AbstractProject)item).getScm();
        }
        if (scm != null) {
            Logger.getLogger(LOGGER).fine("SCM Key: " + scm.getKey());
        } else {
            Logger.getLogger(LOGGER).fine("SCM Key: is null");
        }
        if (scm != null && scm instanceof GitSCM) {
            GitSCM git = (GitSCM) scm;
            UserRemoteConfig remote = git.getUserRemoteConfigs().get(0);
            remoteURL = remote.getUrl();
        }
        return remoteURL;
    }

}
