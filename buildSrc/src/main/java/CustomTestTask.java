import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.AttachParentTestResultProcessor;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.JUnitXmlReport;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.time.Clock;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;

public class CustomTestTask extends AbstractTestTask {
    private final IdGenerator<Long> idGenerator = new LongIdGenerator();

    public CustomTestTask() {
        DirectoryReport htmlReport = getReports().getHtml();
        JUnitXmlReport xmlReport = getReports().getJunitXml();
        JavaPluginConvention convention = getProject().getConvention().getPlugin(JavaPluginConvention.class);

        xmlReport.getOutputLocation().convention(getProject().getLayout().getProjectDirectory().dir(getProject().provider(() -> new File(convention.getTestResultsDir(), getName()).getAbsolutePath())));
        htmlReport.getOutputLocation().convention(getProject().getLayout().getProjectDirectory().dir(getProject().provider(() -> new File(convention.getTestReportDir(), getName()).getAbsolutePath())));
        getBinaryResultsDirectory().convention(getProject().getLayout().getProjectDirectory().dir(getProject().provider(() -> new File(convention.getTestResultsDir(), getName() + "/binary").getAbsolutePath())));
    }

    @Inject
    public BuildOperationExecutor getBuildOperationExcecutor() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public Clock getClock() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected TestExecuter<? extends TestExecutionSpec> createTestExecuter() {
        Object testTaskOperationId = getBuildOperationExcecutor().getCurrentOperation().getParentId();

        return new TestExecuter<TestExecutionSpec>() {
            @Override
            public void execute(TestExecutionSpec testExecutionSpec, TestResultProcessor testResultProcessor) {
                AttachParentTestResultProcessor resultProcessor = new AttachParentTestResultProcessor(testResultProcessor);

                RootTestSuiteDescriptor rootSuite = new RootTestSuiteDescriptor(getPath(), "Test execution for: " + getPath(), testTaskOperationId);
                resultProcessor.started(rootSuite, new TestStartEvent(getClock().getCurrentTime()));

                TestDescriptorInternal testClass = new DefaultTestClassDescriptor(idGenerator.generateId(), "org.elasticsearch.foo.Class");  // Using DefaultTestClassDescriptor to fake JUnit test
                resultProcessor.started(testClass, new TestStartEvent(getClock().getCurrentTime()));

                TestDescriptorInternal testDescriptor = new DefaultTestMethodDescriptor(idGenerator.generateId(), testClass.getClassName(), "bar method");
                resultProcessor.started(testDescriptor, new TestStartEvent(getClock().getCurrentTime()).withParentId(testClass.getId()));
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                resultProcessor.completed(testDescriptor.getId(), new TestCompleteEvent(getClock().getCurrentTime(), TestResult.ResultType.SUCCESS));

                resultProcessor.completed(testClass.getId(), new TestCompleteEvent(getClock().getCurrentTime(), TestResult.ResultType.SUCCESS));

                resultProcessor.completed(rootSuite.getId(), new TestCompleteEvent(getClock().getCurrentTime()));
            }

            @Override
            public void stopNow() {

            }
        };
    }

    @Override
    protected TestExecutionSpec createTestExecutionSpec() {
        return new TestExecutionSpec() {};
    }

    public static final class RootTestSuiteDescriptor extends DefaultTestSuiteDescriptor {
        private final Object testTaskOperationId;

        private RootTestSuiteDescriptor(Object id, String name, Object testTaskOperationId) {
            super(id, name);
            this.testTaskOperationId = testTaskOperationId;
        }

        @Nullable
        @Override
        public Object getOwnerBuildOperationId() {
            return testTaskOperationId;
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}
