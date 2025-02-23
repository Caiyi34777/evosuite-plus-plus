package evosuite.shell.listmethod;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.evosuite.TestGenerationContext;
import org.evosuite.classpath.ResourceList;
import org.evosuite.coverage.dataflow.DefUseFactory;
import org.evosuite.coverage.dataflow.DefUsePool;
import org.evosuite.coverage.dataflow.Definition;
import org.evosuite.coverage.dataflow.Use;
import org.evosuite.graphs.GraphPool;
import org.evosuite.graphs.cfg.ActualControlFlowGraph;
import org.evosuite.graphs.cfg.BytecodeAnalyzer;
import org.evosuite.graphs.cfg.BytecodeInstruction;
import org.evosuite.graphs.cfg.CFGFrame;
import org.evosuite.graphs.interprocedural.DefUseAnalyzer;
import org.evosuite.utils.CollectionUtil;
import org.evosuite.utils.CommonUtility;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.tree.analysis.Value;
import org.slf4j.Logger;

import evosuite.shell.utils.LoggerUtils;
import evosuite.shell.utils.OpcodeUtils;

/**
 * 
 * @author thilyly_tran
 * 
 */
public class MethodFlagCondFilter implements IMethodFilter {
	private static Logger log = LoggerUtils.getLogger(MethodFlagCondFilter.class);
	
//	@Override
//	public List<String> listTestableMethods(Class<?> targetClass, ClassLoader classLoader) throws IOException, AnalyzerException {
//		InputStream is = ResourceList.getInstance(TestGenerationContext.getInstance().getClassLoaderForSUT())
//				.getClassAsStream(targetClass.getName());
//		List<String> validMethods = new ArrayList<String>();
//		try {
//			ClassReader reader = new ClassReader(is);
//			ClassNode cn = new ClassNode();
//			reader.accept(cn, ClassReader.SKIP_FRAMES);
//			List<MethodNode> l = cn.methods;
//			// Filter out abstract class
//			if ((cn.access & Opcodes.ACC_ABSTRACT) == Opcodes.ACC_ABSTRACT) {
//				return new ArrayList<String>();
//			}
//			for (MethodNode m : l) {
//				/* methodName should be the same as declared in evosuite: String methodName = method.getName() + Type.getMethodDescriptor(method); */
//				String methodName = CommonUtility.getMethodName(m);
//	
//				if ((m.access & Opcodes.ACC_PUBLIC) == Opcodes.ACC_PUBLIC
//						|| (m.access & Opcodes.ACC_PROTECTED) == Opcodes.ACC_PROTECTED
//						|| (m.access & Opcodes.ACC_PRIVATE) == 0 /* default */ ) {
//					
//                    if (methodName.contains("<init>") || methodName.contains("<clinit>")) {
//                        continue;
//                    }
//
//                    String className = targetClass.getName();
//                    ActualControlFlowGraph cfg = GraphPool.getInstance(classLoader).getActualCFG(className,
//                            methodName);
//                    if (cfg == null) {
//                        BytecodeAnalyzer bytecodeAnalyzer = new BytecodeAnalyzer();
//                        bytecodeAnalyzer.analyze(classLoader, className, methodName, m);
//                        bytecodeAnalyzer.retrieveCFGGenerator().registerCFGs();
//                        cfg = GraphPool.getInstance(classLoader).getActualCFG(className, methodName);
//                    }
//                    
//                    if (getIfBranchesInMethod(cfg).isEmpty()) {
//                    	continue;
//                    }
//
//					try {
//						if (checkMethod(classLoader, targetClass.getName(), methodName, m, cn)) {
//							validMethods.add(methodName);
//						}
//					} catch (Exception e) {
//						log.info("error!!", e);
//					}
//				} 
//			}
//		} finally {
//			is.close(); 
//		}
//		return validMethods;
//	}
	
	@Override
	public List<String> listTestableMethods(Class<?> targetClass, ClassLoader classLoader) throws IOException, AnalyzerException {
		InputStream is = ResourceList.getInstance(TestGenerationContext.getInstance().getClassLoaderForSUT())
				.getClassAsStream(targetClass.getName());
		List<String> validMethods = new ArrayList<String>();
		try {
			ClassReader reader = new ClassReader(is);
			ClassNode cn = new ClassNode();
			reader.accept(cn, ClassReader.SKIP_FRAMES);
			List<MethodNode> l = cn.methods;
			// Filter out abstract class
			if ((cn.access & Opcodes.ACC_ABSTRACT) == Opcodes.ACC_ABSTRACT) {
				return new ArrayList<String>();
			}
			for (MethodNode m : l) {
				/* methodName should be the same as declared in evosuite: String methodName = method.getName() + Type.getMethodDescriptor(method); */
				String methodName = CommonUtility.getMethodName(m);
	
				if ((m.access & Opcodes.ACC_PUBLIC) == Opcodes.ACC_PUBLIC
						|| (m.access & Opcodes.ACC_PROTECTED) == Opcodes.ACC_PROTECTED
						|| (m.access & Opcodes.ACC_PRIVATE) == 0 /* default */ ) {
					
                    if (methodName.contains("<init>") || methodName.contains("<clinit>")) {
                        continue;
                    }

                    String className = targetClass.getName();
                    ActualControlFlowGraph cfg = GraphPool.getInstance(classLoader).getActualCFG(className,
                            methodName);
                    if (cfg == null) {
                        BytecodeAnalyzer bytecodeAnalyzer = new BytecodeAnalyzer();
                        bytecodeAnalyzer.analyze(classLoader, className, methodName, m);
                        bytecodeAnalyzer.retrieveCFGGenerator().registerCFGs();
                        cfg = GraphPool.getInstance(classLoader).getActualCFG(className, methodName);
                    }
                    
                    if (getIfBranchesInMethod(cfg).isEmpty()) {
                    	continue;
                    }

					try {
						//TODO Cheng Yan add method filter
						String methodSig = className + "#" + methodName;
//						if(ListMethods.interestedMethods.contains(methodSig)) {
//							tMethodSb.append(CommonUtility.getMethodId(className, methodName)).append("\n");
							if (checkMethod(classLoader, targetClass.getName(), methodName, m, cn)) {
								validMethods.add(methodName);
							}
//						}
					} catch (Exception e) {
						log.info("error!!", e);
					}
				} 
			}
		} finally {
			is.close(); 
		}
		return validMethods;
	}
	
	
	
	protected Set<BytecodeInstruction> getIfBranchesInMethod(ActualControlFlowGraph cfg) {
		Set<BytecodeInstruction> ifBranches = new HashSet<BytecodeInstruction>();
		for (BytecodeInstruction b : cfg.getBranches()) {
			if (b.isBranch()) {
				if (b.getASMNode().getOpcode() == Opcodes.JSR || b.getASMNode().getOpcode() == Opcodes.GOTO) {
					continue;
				}
				ifBranches.add(b);
			}
		}
		return ifBranches;
	}

	/**
	 * @throws ClassNotFoundException 
	 * 
	 */
	protected boolean checkMethod(ClassLoader classLoader, String className, String methodName, MethodNode node,
			ClassNode cn) throws AnalyzerException, IOException, ClassNotFoundException {
		log.debug(String.format("#Method %s#%s", className, methodName));
//		GraphPool.clearAll();
		ActualControlFlowGraph cfg = GraphPool.getInstance(classLoader).getActualCFG(className, methodName);
		if (cfg == null) {
			BytecodeAnalyzer bytecodeAnalyzer = new BytecodeAnalyzer();
			bytecodeAnalyzer.analyze(classLoader, className, methodName, node);
			bytecodeAnalyzer.retrieveCFGGenerator().registerCFGs();
			cfg = GraphPool.getInstance(classLoader).getActualCFG(className, methodName);
		}
		
		boolean defuseAnalyzed = false;
		Map<String, Boolean> methodValidityMap = new HashMap<String, Boolean>();
		for (BytecodeInstruction insn : getIfBranchesInMethod(cfg)) {
			AbstractInsnNode insnNode = insn.getASMNode();
			if (CollectionUtil.existIn(insnNode .getOpcode(), Opcodes.IFEQ, Opcodes.IFNE)) {
				StringBuilder sb = new StringBuilder()
							.append(OpcodeUtils.getCode(insnNode.getOpcode()))
							.append(", prev -- ")
							.append(OpcodeUtils.getCode(insnNode.getPrevious().getOpcode()));
				log.info(sb.toString());
				CFGFrame frame = insn.getFrame();
				Value value = frame.getStack(0);
				if (value instanceof SourceValue) {
					SourceValue srcValue = (SourceValue) value;
					AbstractInsnNode condDefinition = (AbstractInsnNode) srcValue.insns.iterator().next();
					if (CommonUtility.isInvokeMethodInsn(condDefinition)) {
						if (checkInvokedMethod(classLoader, condDefinition, methodValidityMap)) {
							log.info("!FOUND IT! in method " + methodName);
							return true;
						}
					} else {
						BytecodeInstruction condBcDef = cfg.getInstruction(node.instructions.indexOf(condDefinition));
						if (condBcDef.isUse()) {
							if (!defuseAnalyzed) {
								DefUseAnalyzer defUseAnalyzer = new DefUseAnalyzer();
								defUseAnalyzer.analyze(classLoader, node, className, methodName, node.access);
								defuseAnalyzed = true;
							}
							Use use = DefUseFactory.makeUse(condBcDef);
							List<Definition> defs = DefUsePool.getDefinitions(use); // null if it is a method parameter.
							Definition lastDef = null;
							for (Definition def : CollectionUtil.nullToEmpty(defs)) {
								if (lastDef == null || def.getInstructionId() > lastDef.getInstructionId()) {
									lastDef = def;
								}
							}
							if (lastDef != null && CommonUtility.isInvokeMethodInsn(lastDef.getASMNode())) {
								if (checkInvokedMethod(classLoader, lastDef.getASMNode(), methodValidityMap)) {
									log.info("!FOUND IT! in method " + methodName);
									return true;
								}
							}
						}
					}
				}
			}
		}
		return false;
	}

	protected boolean checkInvokedMethod(ClassLoader classLoader, AbstractInsnNode condDefinition, Map<String, Boolean> methodValidityMap)
			throws AnalyzerException, IOException {
		return true;
	}

}
