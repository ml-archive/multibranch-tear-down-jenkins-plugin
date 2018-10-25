# Multibranch Tear Down

This plugin only works with Multibranch pipeline projects. When a particular branch is delete this plugin will trigger
another job with the `git_url` and `branch_name` of the job being deleted. This way you can perform any cleanup
you desired like bringing down servers. 

By default this plugin looks for a job called `job-tear-down-executor`. You may also specify 
the job to use by going to Manage Jenkins and configuring the Job Name under `MultiBranch Tear Down Plugin`, 
additionally you may use `branchTearDownExecutor` in your pipeline to specify a specific job to use for that
branch.

Example in pipeline

```
branchTearDownExecutor jobName: 'my-special-executor'
```

License

Released under the MIT license. See [See LICENSE](https://github.com/fuzz-productions/multibranch-tear-down-jenkins-plugin/blob/master/LICENSE) for details.