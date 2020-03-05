/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.engine.AsyncStackTraceProvider
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.ui.impl.watch.MethodsTracker
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.xdebugger.frame.XStackFrame
import org.jetbrains.kotlin.idea.debugger.canRunEvaluation
import org.jetbrains.kotlin.idea.debugger.coroutine.data.AfterCoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.data.ContinuationHolder
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.hopelessAware
import org.jetbrains.kotlin.idea.debugger.isInKotlinSources
import org.jetbrains.kotlin.idea.debugger.safeMethod

class CoroutineAsyncStackTraceProvider : AsyncStackTraceProvider {

    override fun getAsyncStackTrace(stackFrame: JavaStackFrame, suspendContext: SuspendContextImpl): List<CoroutineStackFrameItem>? {
        val stackFrameList = hopelessAware {
            lookupForPreflightFrame(stackFrame.stackFrameProxy, suspendContext)
        }
        return if (stackFrameList == null || stackFrameList.isEmpty())
            null
        else stackFrameList
    }

    fun findCoroutineStackFrame(
        frameProxy: StackFrameProxyImpl,
        suspendContext: SuspendContextImpl
    ): List<CoroutineStackFrameItem>? {
        val location = frameProxy.location()
        if (!location.isInKotlinSources())
            return null

        val method = location.safeMethod() ?: return null

        if (supportEvaluation(suspendContext, frameProxy.threadProxy())) {
            val context = ExecutionContext(EvaluationContextImpl(suspendContext, frameProxy), frameProxy)
            return ContinuationHolder.lookup(context, method, frameProxy.threadProxy())?.getAsyncStackTraceIfAny()
        }
        return null
    }


    fun lookupForPreflightFrame(
        frameProxy: StackFrameProxyImpl,
        suspendContext: SuspendContextImpl
    ): List<CoroutineStackFrameItem>? {
        val location = frameProxy.location()
        if (!location.isInKotlinSources())
            return null

        val method = location.safeMethod() ?: return null
        val threadProxy = frameProxy.threadProxy()


        if (supportEvaluation(suspendContext, threadProxy)) {
            if (ContinuationHolder.lookupPreFlight(method)) {
                var frames = threadProxy.frames()
                val preflightIndex = frames.indexOf(frameProxy)
                val resumeWithFrame = frames[preflightIndex + 1]
                val stackFrame = mutableListOf<CoroutineStackFrameItem>()
                if (ContinuationHolder.isResumeMethodFrame(resumeWithFrame.location().method())) {
                    val context = ExecutionContext(EvaluationContextImpl(suspendContext, resumeWithFrame), resumeWithFrame)
                    var continuationStack = ContinuationHolder.lookupForResumeContinuation(
                            context,
                            resumeWithFrame.location().method(),
                            resumeWithFrame.threadProxy()
                        )
                        ?.getAsyncStackTraceIfAny()
                    if (continuationStack != null)
                        stackFrame.addAll(continuationStack)
                }

                for (index in preflightIndex + 2..frames.size-1) {
                    val frame = frames[index]
                    stackFrame.add(AfterCoroutineStackFrameItem(frame))
                }
                return stackFrame
            }
        }

        return null
    }

    fun lookupForResumeContinuation(
        frameProxy: StackFrameProxyImpl,
        suspendContext: SuspendContextImpl
    ): List<CoroutineStackFrameItem>? {
        val location = frameProxy.location()
        if (!location.isInKotlinSources())
            return null

        val method = location.safeMethod() ?: return null

        if (supportEvaluation(suspendContext, frameProxy.threadProxy())) {
            val context = ExecutionContext(EvaluationContextImpl(suspendContext, frameProxy), frameProxy)
            return ContinuationHolder.lookupForResumeContinuation(context, method, frameProxy.threadProxy())?.getAsyncStackTraceIfAny()
        }
        return null
    }

    private fun supportEvaluation(suspendContext: SuspendContextImpl, threadProxy: ThreadReferenceProxyImpl): Boolean =
        threadProxy.threadReference != null && threadProxy.threadReference.isSuspended && canRunEvaluation(suspendContext)

    private fun canRunEvaluation(suspendContext: SuspendContextImpl) =
        suspendContext.debugProcess.canRunEvaluation
}

