package org.apache.aries.rsa.itests.felix;

import java.lang.reflect.Method;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.ops4j.pax.exam.ExamSystem;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.TestContainer;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.PaxExamRuntime;

public class TwoContainerPaxExam extends PaxExam {

    private Class<?> testClass;

    public TwoContainerPaxExam(Class<?> klass) throws InitializationError {
        super(klass);
        this.testClass = klass;
    }

    @Override
    public void run(RunNotifier notifier) {
        TestContainer remoteContainer = null;
        try {
            
            ExamSystem testSystem = PaxExamRuntime.createTestSystem(remoteConfig());
            remoteContainer = PaxExamRuntime.createContainer(testSystem);
            remoteContainer.start();
            super.run(notifier);

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (remoteContainer != null) {
                remoteContainer.stop();
            }
        }
        
    }

    private Option[] remoteConfig() throws Exception {
        Object testO = this.testClass.newInstance();
        Method configMethod = getServerConfigMethod();
        return (Option[])configMethod.invoke(testO);
    }

    private Method getServerConfigMethod() throws NoSuchMethodException, SecurityException {
        Method[] methods = testClass.getMethods();
        for (Method method : methods) {
            if (method.getAnnotation(ServerConfiguration.class) != null) {
                if (method.getParameterTypes().length > 0) {
                    throw new IllegalArgumentException("ServerConfiguration method must have no params");
                }
                if (method.getReturnType() != Option[].class) {
                    throw new IllegalArgumentException("ServerConfiguration method must return Option[]");
                }
                return method;
            }
        }
        throw new IllegalArgumentException("One method must be annotated with @ServerConfiguration");
    }
}
