package evosuite.shell.listmethod;

import java.io.IOException;
import java.util.ListIterator;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;

import evosuite.shell.FileUtils;
import evosuite.shell.Settings;

public class ListRelevantJdkClasses extends MethodFlagCondFilter implements IMethodFilter {
	private String listFile = Settings.getReportFolder() + "/jdkClasses.txt";
	
	@Override
	protected boolean checkMethod(ClassLoader classLoader, String className, String methodName, MethodNode node,
			ClassNode cn) throws AnalyzerException, IOException, ClassNotFoundException {
		boolean valid = super.checkMethod(classLoader, className, methodName, node, cn);
		if (valid) {
			StringBuilder sb = new StringBuilder();
			ListIterator<AbstractInsnNode> it = node.instructions.iterator();
			while(it.hasNext()) {
				AbstractInsnNode insn = it.next();
				if (insn instanceof MethodInsnNode) {
					MethodInsnNode methodInsn = (MethodInsnNode) insn;
					String owner = methodInsn.owner;
					if (owner.startsWith("java/") 
							|| owner.startsWith("javax/")
							|| owner.startsWith("sun/")
							|| owner.startsWith("com/sun/")) {
						sb.append(owner).append("\n");
					}
				}
			}
			FileUtils.writeFile(listFile, sb.toString(), true);
		}
		return valid;
	}
	
	public String getListFile() {
		return listFile;
	}
}
