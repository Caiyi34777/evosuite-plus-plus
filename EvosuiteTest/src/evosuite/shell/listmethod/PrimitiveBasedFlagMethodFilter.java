package evosuite.shell.listmethod;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.lang3.StringUtils;
import org.evosuite.Properties;
import org.evosuite.classpath.ResourceList;
import org.evosuite.coverage.dataflow.DefUseFactory;
import org.evosuite.coverage.dataflow.DefUsePool;
import org.evosuite.coverage.dataflow.Definition;
import org.evosuite.coverage.dataflow.Use;
import org.evosuite.coverage.fbranch.FlagEffectEvaluator;
import org.evosuite.graphs.GraphPool;
import org.evosuite.graphs.cdg.ControlDependenceGraph;
import org.evosuite.graphs.cdg.DominatorTree;
import org.evosuite.graphs.cfg.ActualControlFlowGraph;
import org.evosuite.graphs.cfg.BasicBlock;
import org.evosuite.graphs.cfg.BytecodeAnalyzer;
import org.evosuite.graphs.cfg.BytecodeInstruction;
import org.evosuite.graphs.cfg.BytecodeInstructionPool;
import org.evosuite.graphs.cfg.CFGFrame;
import org.evosuite.graphs.cfg.ControlDependency;
import org.evosuite.runtime.instrumentation.RuntimeInstrumentation;
import org.evosuite.utils.CollectionUtil;
import org.evosuite.utils.CommonUtility;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.tree.analysis.Value;
import org.slf4j.Logger;

import com.sun.tools.classfile.Opcode;

import evosuite.shell.DefUseAnalyzer;
import evosuite.shell.EvosuiteForMethod;
import evosuite.shell.Settings;
import evosuite.shell.excel.ExcelWriter;
import evosuite.shell.utils.LoggerUtils;
import evosuite.shell.utils.OpcodeUtils;

public class PrimitiveBasedFlagMethodFilter extends MethodFlagCondFilter {
	public static final String excelProfileSubfix = "_primitiveFlagMethodProfiles.xlsx";
	private static Logger log = LoggerUtils.getLogger(PrimitiveBasedFlagMethodFilter.class);
	private ExcelWriter writer;

	public PrimitiveBasedFlagMethodFilter() {
		String statisticFile = new StringBuilder(Settings.getReportFolder()).append(File.separator)
				.append(EvosuiteForMethod.projectId).append(excelProfileSubfix).toString();
		File newFile = new File(statisticFile);
		if (newFile.exists()) {
			newFile.delete();
		}
		writer = new ExcelWriter(new File(statisticFile));
		writer.getSheet("data",
				new String[] { "ProjectId", "ProjectName", "Target Method", "Flag Method", "branch", "const0/1",
						"branch", "getfield", "branch", "iLoad", "branch", "invokemethod", "other", "Remarks",
						"has Primitve type", "hasPrimitiveComparison", "isValid" },
				0);
	}

	@Override
	protected boolean checkMethod(ClassLoader classLoader, String className, String methodName, MethodNode node,
			ClassNode cn) throws AnalyzerException, IOException, ClassNotFoundException {
		log.debug(String.format("#Method %s#%s", className, methodName));

		// Get actual CFG for target method
		ActualControlFlowGraph cfg = GraphPool.getInstance(classLoader).getActualCFG(className, methodName);
		if (cfg == null) {
			BytecodeInstructionPool.getInstance(classLoader).registerMethodNode(node, className, node.name + node.desc);
			BytecodeAnalyzer bytecodeAnalyzer = new BytecodeAnalyzer();
			bytecodeAnalyzer.analyze(classLoader, className, methodName, node);
			bytecodeAnalyzer.retrieveCFGGenerator().registerCFGs();
			cfg = GraphPool.getInstance(classLoader).getActualCFG(className, methodName);
		}

		/* Has branches */
		if (CollectionUtil.isNullOrEmpty(cfg.getBranches())) {
			return false;
		}

		/* Not a constructor */
		if (methodName.contains("<init>")) {
			return false;
		}

		/* Must have parameters */
		if (!hasParam(node, cn)) {
			return false;
		}

		/* All parameters must be of primitive type (including String) */
		// if(!FilterHelper.isMethodAtLeastPrimitiveParameter(node)) {
		// return false;
		// }

		// if(FilterHelper.isMethodAtLeastStringParameter(node)) {
		// return false;
		// }

		boolean defuseAnalyzed = false;
		MethodContent mc = new MethodContent();

		/* check parameter types of target method */
		mc.hasPrimitiveParam = FilterHelper.hasAtLeastOnePrimitiveParam(node, cn);

		/* must have at least one primitive parameter */
		if (!mc.hasPrimitiveParam) {
			return false;
		}
		/* Parameter contains interface or abstract class object */
		if (FilterHelper.hasUnsupportedParam(node, cn, classLoader)) {
			return false;
		}

		boolean valid = false;
		Map<String, Boolean> methodValidityMap = new HashMap<>();
		for (BytecodeInstruction insn : cfg.getBranches()) {

			// boolean isBranchDependOnBranchWithAllPrimitives =
			// checkDependentBranchUseAllPrimitiveOperands(insn);
			//// checkDependentBranchUseAllPrimitiveOperands(insn);
			// if(!isBranchDependOnBranchWithAllPrimitives &&
			// !insn.getControlDependencies().isEmpty()) {
			//// checkDependentBranchUseAllPrimitiveOperands(insn);
			// continue;
			// }

			AbstractInsnNode insnNode = insn.getASMNode();

			/* check whether it is a flag condition */
			/* exist potential flag method */
			if (CollectionUtil.existIn(insnNode.getOpcode(), Opcodes.IFEQ, Opcodes.IFNE)) {
				// StringBuilder sb = new StringBuilder()
				// .append(OpcodeUtils.getCode(insnNode.getOpcode()))
				// .append(", prev -- ")
				// .append(OpcodeUtils.getCode(insnNode.getPrevious().getOpcode()));
				// log.info(sb.toString());
				CFGFrame frame = insn.getFrame();
				Value value = frame.getStack(0);
				if (value instanceof SourceValue) {
					SourceValue srcValue = (SourceValue) value;

					// TODO the value could be defined multiple times
					AbstractInsnNode condDefinition = (AbstractInsnNode) srcValue.insns.iterator().next();
					/* Next instruction is to invokeMethod followed by IFEQ or IFNE */
					if (CommonUtility.isInvokeMethodInsn(condDefinition)) {

						if (checkFlagMethod(classLoader, condDefinition, insn.getLineNumber(), mc, methodValidityMap)) {
							log.info("!FOUND IT! in method " + methodName);
							valid = true;
						}
					} else {
						BytecodeInstruction condBcDef = cfg.getInstruction(node.instructions.indexOf(condDefinition));
						/* isFieldUse or isLocalVariableUse or isArrayLoadInstruction */
						// Check if the insn is use
						if (condBcDef.isUse()) {
							if (!defuseAnalyzed) {
								DefUseAnalyzer instr = new DefUseAnalyzer();
								instr.analyze(classLoader, node, className, methodName, node.access);
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
								if (checkFlagMethod(classLoader, lastDef.getASMNode(), insn.getLineNumber(), mc,
										methodValidityMap)) {
									log.info("!FOUND IT! in method " + methodName);
									valid = true;
								}
							}
						}
					}
				}
			}
		}
		//		logToExcel(mc, className, methodName);
		return valid;
	}

	protected boolean hasParam(MethodNode mn, ClassNode cn) {
		try {
			Type[] argTypes = Type.getArgumentTypes(mn.desc);
			return argTypes.length != 0;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	protected void logToExcel(MethodContent mc, String className, String methodName) throws IOException {
		List<List<Object>> data = new ArrayList<>();
		String methodFullName = className + "#" + methodName;
		for (FlagMethod fm : mc.flagMethods) {
			List<Object> rowData = new ArrayList<>();
			rowData.add(EvosuiteForMethod.projectId);
			rowData.add(EvosuiteForMethod.projectName);
			rowData.add(methodFullName);
			rowData.add(fm.methodName);
			rowData.add(fm.branch);
			rowData.add(fm.rConst);
			rowData.add(fm.rConstBranch);
			rowData.add(fm.getField);
			rowData.add(fm.rGetFieldBranch);
			rowData.add(fm.iload);
			rowData.add(fm.rIloadBranch);
			rowData.add(fm.invokeMethods);
			rowData.add(fm.other);
			rowData.add(StringUtils.join(fm.notes, "\n"));
			rowData.add(mc.hasPrimitiveParam);
			rowData.add(fm.hasInterestedPrimitiveCompareCond);
			rowData.add(fm.valid);
			data.add(rowData);
		}
		writer.writeSheet("data", data);
	}

	protected boolean checkFlagMethod(ClassLoader classLoader, AbstractInsnNode flagDefIns,
			int calledLineInTargetMethod, MethodContent mc, Map<String, Boolean> visitMethods)
					throws AnalyzerException, IOException, ClassNotFoundException {
		FlagMethod flagMethod = new FlagMethod();

		MethodInsnNode methodInsn = null;
		String className = null;
		String methodName = null;
		/**
		 * a set of condition check
		 */
		if (flagDefIns.getOpcode() == Opcodes.INVOKEDYNAMIC) {
			InvokeDynamicInsnNode idInsn = (InvokeDynamicInsnNode) flagDefIns;
			flagMethod.methodName = idInsn.name + idInsn.desc;
		} else {
			methodInsn = (MethodInsnNode) flagDefIns;
			className = methodInsn.owner.replace("/", ".");
			methodName = CommonUtility.getMethodName(methodInsn.name, methodInsn.desc);

			flagMethod.methodName = className + "#" + methodName;
		}

		if (visitMethods.containsKey(flagMethod.methodName)) {
			flagMethod.valid = visitMethods.get(flagMethod.methodName);
			return flagMethod.valid;
		}

		mc.flagMethods.add(flagMethod);
		// java.lang.Object not allowed here
		if (!RuntimeInstrumentation.checkIfCanInstrument(className)) {
			flagMethod.notes.add(Remarks.UNINSTRUMENTABLE.text);
			visitMethods.put(flagMethod.methodName, false);
			return false;
		}

		MethodNode methodNode = getMethod(classLoader, methodInsn, className);
		if (methodNode == null) {
			flagMethod.notes.add(Remarks.NO_SOURCE.text);
			visitMethods.put(flagMethod.methodName, false);
			return false;
		}

		// Called method should have at least one parameter
		if (!FilterHelper.isMethodAtLeastPrimitiveParameter(methodNode)) {
			return false;
		}

		try {
			// GraphPool.clearAll();
			/**
			 * we get the cfg for called method here.
			 */
			ActualControlFlowGraph cfg = GraphPool.getInstance(classLoader).getActualCFG(className, methodName);

			if (cfg == null) {
				// generateCDG(classLoader, className, methodNode);
				// cfg = GraphPool.getInstance(classLoader).getActualCFG(className, methodName);
				BytecodeInstructionPool.getInstance(classLoader).registerMethodNode(methodNode, className,
						methodNode.name + methodNode.desc);
				BytecodeAnalyzer bytecodeAnalyzer = new BytecodeAnalyzer();
				bytecodeAnalyzer.analyze(classLoader, className, methodName, methodNode);
				bytecodeAnalyzer.retrieveCFGGenerator().registerCFGs();
				cfg = GraphPool.getInstance(classLoader).getActualCFG(className, methodName);
				// GraphPool.getInstance(classLoader).alwaysRegisterActualCFG(cfg);
			}
			//			else {
			//
			//				System.currentTimeMillis();
			//			}
			//			
			if (CollectionUtil.getSize(cfg.getBranches()) < 1) {
				flagMethod.notes.add(Remarks.NOBRANCH.text);
				visitMethods.put(flagMethod.methodName, false);
				return false;
			}

			//			System.currentTimeMillis();

			flagMethod.branch = cfg.getBranches().size();

			//			BytecodeInstruction ins = cfg.getBranches().iterator().next();
			//			ControlDependenceGraph g = ins.getCDG();
			// System.out.print(g);
			Set<BytecodeInstruction> exitPoints = cfg.getExitPoints();
			boolean valid = checkReturnDependOnBranchUsingAllPrimitiveOperands(className, exitPoints);
			visitMethods.put(flagMethod.methodName, valid);
			flagMethod.valid = valid;
			return valid;

			// DominatorTree<BasicBlock> dominatorTree = new DominatorTree<BasicBlock>(cfg);
			// /**
			// * for each exit (i.e., return) instruction, ...
			// */
			// for (BytecodeInstruction exit : exitPoints) {
			// if (OpcodeUtils.isReturnInsn(exit.getASMNode().getOpcode())) {
			// AbstractInsnNode returnDef = getDefinitionInsn(exit);
			// flagMethod.notes.add("Invoked Line Number: " + calledLineInTargetMethod);
			//
			// if (returnDef instanceof MethodInsnNode) {
			// flagMethod.invokeMethods ++;
			// valid |= checkFlagMethod(classLoader, returnDef, calledLineInTargetMethod,
			// mc, visitMethods);
			// } else if (CollectionUtil.existIn(returnDef.getOpcode(), Opcodes.ICONST_0,
			// Opcodes.ICONST_1)) {
			// flagMethod.rConst ++;
			// valid = true;
			// BytecodeInstruction defBcInsn =
			// exit.getActualCFG().getInstruction(methodNode.instructions.indexOf(returnDef));
			// if (dominatorTree.getImmediateDominator(defBcInsn.getBasicBlock()) != null) {
			// flagMethod.rConstBranch = 1;
			// flagMethod.hasInterestedPrimitiveCompareCond =
			// hasPrimitiveCompareCondConstrainst(dominatorTree, defBcInsn,
			// exit.getActualCFG(), methodNode,
			// classLoader, className);
			// }
			// } else if (CollectionUtil.existIn(returnDef.getOpcode(), Opcodes.GETFIELD)) {
			// valid = true;
			// BytecodeInstruction defBcInsn =
			// exit.getActualCFG().getInstruction(methodNode.instructions.indexOf(returnDef));
			// if (dominatorTree.getImmediateDominator(defBcInsn.getBasicBlock()) != null) {
			// flagMethod.rGetFieldBranch = 1;
			// flagMethod.hasInterestedPrimitiveCompareCond =
			// hasPrimitiveCompareCondConstrainst(dominatorTree, defBcInsn,
			// exit.getActualCFG(), methodNode,
			// classLoader, className);
			// }
			// flagMethod.getField ++;
			// } else if (CollectionUtil.existIn(returnDef.getOpcode(), Opcodes.ILOAD)) {
			// valid = true;
			// BytecodeInstruction defBcInsn =
			// exit.getActualCFG().getInstruction(methodNode.instructions.indexOf(returnDef));
			// if (dominatorTree.getImmediateDominator(defBcInsn.getBasicBlock()) != null) {
			// flagMethod.rIloadBranch = 1;
			// flagMethod.hasInterestedPrimitiveCompareCond =
			// hasPrimitiveCompareCondConstrainst(dominatorTree, defBcInsn,
			// exit.getActualCFG(), methodNode,
			// classLoader, className);
			// }
			// flagMethod.iload ++;
			// } else {
			// flagMethod.other ++;
			// if (returnDef.getOpcode() == -1) {
			// flagMethod.notes.add(returnDef.getClass().getSimpleName());
			// } else {
			// flagMethod.notes.add(OpcodeUtils.getCode(returnDef.getOpcode()));
			// }
			// }
			// }
			// }

		} catch (Exception e) {
			log.debug("error!!", e);
			visitMethods.put(flagMethod.methodName, false);
			return false;
		}
	}

	private void generateCDG(ClassLoader classLoader, String className, MethodNode mn) {
		if (BytecodeInstructionPool.getInstance(classLoader).hasMethod(className, mn.name + mn.desc))
			return;

		BytecodeInstructionPool.getInstance(classLoader).registerMethodNode(mn, className, mn.name + mn.desc); // TODO: Adapt for multiple classLoaders

		BytecodeAnalyzer bytecodeAnalyzer = new BytecodeAnalyzer();

		try {

			bytecodeAnalyzer.analyze(classLoader, className, mn.name + mn.desc, mn); // TODO
		} catch (AnalyzerException e) {
			e.printStackTrace();
		}

		// compute Raw and ActualCFG and put both into GraphPool
		bytecodeAnalyzer.retrieveCFGGenerator().registerCFGs();
	}

	private boolean checkReturnDependOnBranchUsingAllPrimitiveOperands(String className,
			Set<BytecodeInstruction> exitPoints) {
		for (BytecodeInstruction exit : exitPoints) {
			if (exit.isReturn()) {
				List<BytecodeInstruction> sourceList = exit.getSourceOfStackInstructionList(0);
				for (BytecodeInstruction source : sourceList) {
					boolean dependentBranchUseAllPrimitiveOperands = checkDependentBranchUseAllPrimitiveOperands(
							source);
					if (dependentBranchUseAllPrimitiveOperands) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private boolean checkDependentBranchUseAllPrimitiveOperands(BytecodeInstruction source) {
		Set<ControlDependency> cds = source.getControlDependencies();
		for (ControlDependency cd : cds) {
			BytecodeInstruction ifBranch = cd.getBranch().getInstruction();

			// TODO should allow string compare null
			if (ifBranch.getASMNode().getOpcode() == Opcodes.IFNONNULL
					|| ifBranch.getASMNode().getOpcode() == Opcodes.IFNULL) {
				continue;
			}

			int numberOfOperands = FlagEffectEvaluator.getOperands(ifBranch);
			// IF condition
			// Only 1 value on the stack is used
			if (numberOfOperands == 1) {
				List<BytecodeInstruction> list = ifBranch.getSourceOfStackInstructionList(0);
				boolean noUseOfObject = checkNoUseOfObject(list);
				if (noUseOfObject) {
					return true;
				}
			}
			// 2 values on the stack will be used
			else if (numberOfOperands == 2) {
				List<BytecodeInstruction> list0 = ifBranch.getSourceOfStackInstructionList(0);
				boolean noUseOfObject1 = checkNoUseOfObject(list0);

				List<BytecodeInstruction> list1 = ifBranch.getSourceOfStackInstructionList(1);
				boolean noUseOfObject2 = checkNoUseOfObject(list1);

				if (noUseOfObject1 && noUseOfObject2) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean checkNoUseOfObject(List<BytecodeInstruction> list) {
		for (BytecodeInstruction defIns : list) {
			if (defIns.isFieldUse()) {
				continue;
			} else if (defIns.isArrayLoadInstruction()) {
				continue;
			} else if (defIns.getASMNode().getOpcode() == Opcodes.ALOAD) {
				continue;
			} else if (defIns.isMethodCall()) {
				MethodInsnNode methodInsnNode = (MethodInsnNode) (defIns.getASMNode());
				if (methodInsnNode.owner.equals("java/lang/String")) {
					return true;
				}

				if (defIns.getCalledMethodsArgumentCount() == 0) {
					continue;
				} else {
					// TODO all the arguments should be primitive
					// boolean isAllParamPrimitive =
					// FilterHelper.isAllMethodParameterPrimitive(methodInsnNode.desc);
					boolean isAllParamPrimitive = FilterHelper.isAtLeastMethodParameterPrimitive(methodInsnNode.desc);
					return isAllParamPrimitive;
				}
			} else {
				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	private int hasPrimitiveCompareCondConstrainst(DominatorTree<BasicBlock> dt, BytecodeInstruction defBcInsn,
			ActualControlFlowGraph cfg, MethodNode node, ClassLoader classLoader, String className) {
		try {
			BasicBlock immediateDominator = defBcInsn.getBasicBlock();
			while (immediateDominator != null) {
				Iterator<BytecodeInstruction> it = immediateDominator.iterator();
				BytecodeInstruction cond = null;
				while (it.hasNext()) {
					BytecodeInstruction detailNode = it.next();
					int opcode = detailNode.getASMNode().getOpcode();
					if (OpcodeUtils.isCondition(opcode) && (opcode != Opcodes.IF_ACMPEQ)
							&& (opcode != Opcodes.IF_ACMPNE)) {
						cond = detailNode;
						break;
					}
				}
				if (cond != null) {
					AbstractInsnNode insnNode = cond.getASMNode();
					if (CollectionUtil.existIn(insnNode.getOpcode(), Opcodes.IFEQ, Opcodes.IFNE, Opcode.IFLT,
							Opcode.IFGE, Opcode.IFGT, Opcode.IFLE)) {
						CFGFrame frame = cond.getFrame();
						Value value = frame.getStack(0);
						if (isFromPrimitiveSource(cfg, node, classLoader, className, value)) {
							return 1;
						}
					} else if (CollectionUtil.existIn(insnNode.getOpcode(), Opcode.IF_ICMPEQ, Opcode.IF_ICMPNE,
							Opcode.IF_ICMPLT, Opcode.IF_ICMPGE, Opcode.IF_ICMPGT, Opcode.IF_ICMPLE)) {
						CFGFrame frame = cond.getFrame();
						Value value1 = frame.getStack(0);
						Value value2 = frame.getStack(1);
						if (isFromPrimitiveSource(cfg, node, classLoader, className, value1)
								|| isFromPrimitiveSource(cfg, node, classLoader, className, value2)) {
							return 1;
						}
					}
				}
				immediateDominator = dt.getImmediateDominator(immediateDominator);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println();
		}
		return 0;
	}

	private boolean isFromPrimitiveSource(ActualControlFlowGraph cfg, MethodNode node, ClassLoader classLoader,
			String className, Value value) {
		if (value instanceof SourceValue) {
			String methodName = CommonUtility.getMethodName(node);
			SourceValue srcValue = (SourceValue) value;
			AbstractInsnNode condDefinition = (AbstractInsnNode) srcValue.insns.iterator().next();
			if (isPrimitiveAssignment(condDefinition)) {
				return true;
			} else {
				BytecodeInstruction condBcDef = cfg.getInstruction(node.instructions.indexOf(condDefinition));
				if (condBcDef.isUse()) {
					new DefUseAnalyzer().analyze(classLoader, node, className, methodName, node.access);
					Use use = DefUseFactory.makeUse(condBcDef);
					List<Definition> defs = DefUsePool.getDefinitions(use); // null if it is a method parameter.
					Definition lastDef = null;
					for (Definition def : CollectionUtil.nullToEmpty(defs)) {
						if (lastDef == null || def.getInstructionId() > lastDef.getInstructionId()) {
							lastDef = def;
						}
					}
					if (lastDef != null && isPrimitiveAssignment(lastDef.getASMNode())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean isPrimitiveAssignment(AbstractInsnNode insn) {
		String code = OpcodeUtils.getCode(insn.getOpcode());
		if (code.startsWith("ICONST") || code.startsWith("FCONST") || code.startsWith("DCONST")
				|| code.startsWith("LCONST")) {
			return true;
		}
		return false;
	}

	private AbstractInsnNode getDefinitionInsn(BytecodeInstruction ireturnNode) {
		AbstractInsnNode prevInsn = ireturnNode.getASMNode().getPrevious();
		if (prevInsn.getOpcode() < 0) {
			BytecodeInstruction node = ireturnNode;
			CFGFrame frame = node.getFrame();
			while (frame == null) {
				frame = node.getPreviousInstruction().getFrame();
			}
			Value value = frame.getStack(0);
			if (value instanceof SourceValue) {
				SourceValue srcValue = (SourceValue) value;
				return (AbstractInsnNode) srcValue.insns.iterator().next();
			}
		}

		return prevInsn;
	}

	private MethodNode getMethod(ClassLoader classLoader, MethodInsnNode methodInsn, String className)
			throws ClassNotFoundException, IOException {
		InputStream is = null;
		try {
			if (methodInsn.owner.startsWith("java")) {
				is = ResourceList.getInstance(this.getClass().getClassLoader()).getClassAsStream(className);
			} else {
				is = ResourceList.getInstance(classLoader).getClassAsStream(className);
			}
			if (is == null) {
				is = getClassAsStream(className);
			}
			Class<?> targetClass = classLoader.loadClass(className);
			if (targetClass.isInterface()) {
				return null;
			}
			ClassReader reader = new ClassReader(is);
			ClassNode cn = new ClassNode();
			reader.accept(cn, ClassReader.SKIP_FRAMES);
			List<MethodNode> l = cn.methods;
			if ((cn.access & Opcodes.ACC_ABSTRACT) == Opcodes.ACC_ABSTRACT) {
				return null;
			}
			for (MethodNode m : l) {
				if (m.name.equals(methodInsn.name) && m.desc.equals(methodInsn.desc)) {
					dump(m);
					return m;
				}
			}
		} finally {
			if (is != null) {
				is.close();
			}
		}
		return null;
	}

	private void dump(MethodNode m) {
		ListIterator it = m.instructions.iterator();
		while (it.hasNext()) {
			Object node = it.next();
			System.out.println(node);
		}
	}

	private InputStream getClassAsStream(String name) throws IOException {
		String path = name.replace('.', '/') + ".class";
		String windowsPath = name.replace(".", "\\") + ".class";
		String[] cpEntries = Properties.CP.split(File.pathSeparator);
		for (String cpEntry : cpEntries) {
			if (cpEntry.endsWith(".jar")) {
				JarFile jar = new JarFile(cpEntry);
				JarEntry entry = jar.getJarEntry(path);
				if (entry != null) {
					InputStream is = null;
					try {
						is = jar.getInputStream(entry);
						return is;
					} catch (IOException e) {
						// ignore
					}
				}
			}
		}
		return null;
	}

	static class MethodContent {
		boolean hasPrimitiveParam;
		List<FlagMethod> flagMethods = new ArrayList<>();
	}

	private static class FlagMethod {
		private String methodName;
		private int branch;
		private int rConst;
		private int rConstBranch;
		private int getField;
		private int rGetFieldBranch;
		private int iload;
		private int rIloadBranch;
		private int invokeMethods;
		private int other;
		private int hasInterestedPrimitiveCompareCond;
		private List<String> notes = new ArrayList<>();
		private boolean valid = false;
	}

	public static enum Remarks {
		UNINSTRUMENTABLE("Cannot instrument!"), NOBRANCH("No branch!"),
		NO_SOURCE("Could not analyze (Does not have explicit code)!"),;

		private String text;

		private Remarks(String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}
	}
}
