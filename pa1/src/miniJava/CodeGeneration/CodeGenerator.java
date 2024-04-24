package miniJava.CodeGeneration;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGeneration.x64.*;
import miniJava.CodeGeneration.x64.ISA.*;

public class CodeGenerator implements Visitor<Object, Object> {
	private ErrorReporter _errors;
	private InstructionList _asm; // our list of instructions that are used to make the code section
	
	public CodeGenerator(ErrorReporter errors) {
		this._errors = errors;
	}
	
	public void parse(Package prog) {
		_asm = new InstructionList();
		
		// If you haven't refactored the name "ModRMSIB" to something like "R",
		//  go ahead and do that now. You'll be needing that object a lot.
		// Here is some example code.
		
		// Simple operations:
		// _asm.add( new Push(0) ); // push the value zero onto the stack
		// _asm.add( new Pop(Reg64.RCX) ); // pop the top of the stack into RCX
		
		// Fancier operations:
		// _asm.add( new Cmp(new ModRMSIB(Reg64.RCX,Reg64.RDI)) ); // cmp rcx,rdi
		// _asm.add( new Cmp(new ModRMSIB(Reg64.RCX,0x10,Reg64.RDI)) ); // cmp [rcx+0x10],rdi
		// _asm.add( new Add(new ModRMSIB(Reg64.RSI,Reg64.RCX,4,0x1000,Reg64.RDX)) ); // add [rsi+rcx*4+0x1000],rdx
		
		// Thus:
		// new ModRMSIB( ... ) where the "..." can be:
		//  RegRM, RegR						== rm, r
		//  RegRM, int, RegR				== [rm+int], r
		//  RegRD, RegRI, intM, intD, RegR	== [rd+ ri*intM + intD], r
		// Where RegRM/RD/RI are just Reg64 or Reg32 or even Reg8
		//
		// Note there are constructors for ModRMSIB where RegR is skipped.
		// This is usually used by instructions that only need one register operand, and often have an immediate
		//   So they actually will set RegR for us when we create the instruction. An example is:
		// _asm.add( new Mov_rmi(new ModRMSIB(Reg64.RDX,true), 3) ); // mov rdx,3
		//   In that last example, we had to pass in a "true" to indicate whether the passed register
		//    is the operand RM or R, in this case, true means RM
		//  Similarly:
		// _asm.add( new Push(new ModRMSIB(Reg64.RBP,16)) );
		//   This one doesn't specify RegR because it is: push [rbp+16] and there is no second operand register needed
		
		// Patching example:
		// Instruction someJump = new Jmp((int)0); // 32-bit offset jump to nowhere
		// _asm.add( someJump ); // populate listIdx and startAddress for the instruction
		// ...
		// ... visit some code that probably uses _asm.add
		// ...
		// patch method 1: calculate the offset yourself
		//     _asm.patch( someJump.listIdx, new Jmp(asm.size() - someJump.startAddress - 5) );
		// -=-=-=-
		// patch method 2: let the jmp calculate the offset
		//  Note the false means that it is a 32-bit immediate for jumping (an int)
		//     _asm.patch( someJump.listIdx, new Jmp(asm.size(), someJump.startAddress, false) );
		
		prog.visit(this,null);
		
		// Output the file "a.out" if no errors
		if( !_errors.hasErrors() )
			makeElf("a.out");
	}

	@Override
	public Object visitPackage(Package prog, Object arg) {
		int heapPtr = makeMalloc();
		for (ClassDecl c: prog.classDeclList){
			c.visit(this, c);
		}
		return null;
	}

	public void makeElf(String fname) {
		ELFMaker elf = new ELFMaker(_errors, _asm.getSize(), 8); // bss ignored until PA5, set to 8
		elf.outputELF(fname, _asm.getBytes(), ??); // TODO: set the location of the main method
	}
	
	private int makeMalloc() {
		int idxStart = _asm.add( new Mov_rmi(new R(Reg64.RAX,true),0x09) ); // mmap
		
		_asm.add( new Xor(		new R(Reg64.RDI,Reg64.RDI)) 	); // addr=0
		_asm.add( new Mov_rmi(	new R(Reg64.RSI,true),0x1000) ); // 4kb alloc
		_asm.add( new Mov_rmi(	new R(Reg64.RDX,true),0x03) 	); // prot read|write
		_asm.add( new Mov_rmi(	new R(Reg64.R10,true),0x22) 	); // flags= private, anonymous
		_asm.add( new Mov_rmi(	new R(Reg64.R8, true),-1) 	); // fd= -1
		_asm.add( new Xor(		new R(Reg64.R9,Reg64.R9)) 	); // offset=0
		_asm.add( new Syscall() );
		
		// pointer to newly allocated memory is in RAX
		// return the index of the first instruction in this method, if needed
		return idxStart;
	}
	
	private int makePrintln() {
		// TODO: how can we generate the assembly to println?
		return -1;
	}

	////////////////////////////////////
	/* AST CODE GENERATION TRAVERSAL */
	////////////////////////////////////

	////////////////////////////////////
	/* DECLARATIONS */
	////////////////////////////////////

	@Override
	public Object visitClassDecl(ClassDecl clas, Object o){
		for (FieldDecl f: clas.fieldDeclList) {
			_asm.add(new Push(0));
			makeMalloc(); // store resulting ptr in RAX
			_asm.add( new Mov_rmr(new R(Reg64.RBP, -8, Reg64.RAX)));
//			f.visit(this, clas);
		}
		for (MethodDecl m: clas.methodDeclList)
			m.visit(this, clas);
		return null;
	}

	@Override
	public Object visitFieldDecl(FieldDecl f, Object o){
		f.type.visit(this, o);
		return null;
	}

	@Override
	public Object visitMethodDecl(MethodDecl m, Object o){
		// localVar counter to assign offset
		// the nth localVar will have an offset of n * -8 which will be what needs to be added to rbp to access it
		int localVar = 0;
		_asm.add( new Push( Reg64.RBP ) ); // push old rbp onto stack
		_asm.add( new Mov_rmr( new R(Reg64.RBP, Reg64.RSP) ) ); // update rbp to point to wherever rsp is pointing to
		for (Statement s : m.statementList) {
			if (s instanceof VarDeclStmt) {
				_asm.add( new Push(0) ); // allocate room on stack before storing local var in it

				((VarDeclStmt) s).varDecl.offset = (localVar + 1) * -8; // each varDecl will have an offset from rbp
				localVar++;
			}
			s.visit(this, o);
		}
		// update rsp to pnt to rbp (top of call stack, where old rbp was stored)
		_asm.add( new Mov_rrm( new R(Reg64.RSP, Reg64.RBP) ) );
		_asm.add( new Pop( Reg64.RBP ) );	// restore old rbp val
		_asm.add( new Ret() ); // return from method
		return null;
	}
	@Override
	public Object visitParameterDecl(ParameterDecl pd, Object o){
		pd.type.visit(this, o);
		return null;
	}
	@Override
	public Object visitVarDecl(VarDecl vd, Object o){
		vd.type.visit(this, o);
		return null;
	}


	////////////////////////////////////
	/* STATEMENTS */
	////////////////////////////////////

	@Override
	public Object visitBlockStmt(BlockStmt stmt, Object o){
		for (Statement s: stmt.sl) {
			s.visit(this, o);
		}
		return null;
	}
	@Override
	public Object visitVardeclStmt(VarDeclStmt stmt, Object o){
		stmt.varDecl.visit(this, o);
		if (stmt.initExp != null) {
			stmt.initExp.visit(this, o);
			_asm.add( new Pop(Reg64.RAX) ); // get stmt.initExp result from stack
			_asm.add( new Mov_rmr( new R(Reg64.RBP, stmt.varDecl.offset, Reg64.RAX) ) ); // move stmt.initExp into local var [rbp - offset]
		}
		return null;
	}
	@Override
	public Object visitAssignStmt(AssignStmt stmt, Object o){
		// TODO: Check logic for if statements (is all the casting okay?)
		int RBPOffset = 0;
		stmt.ref.visit(this, o);
		if (stmt.ref instanceof IdRef) {
			IdRef ref = (IdRef) stmt.ref;
			if (ref.declaration instanceof VarDecl) { // should always be true??
				RBPOffset = ((VarDecl) ref.declaration).offset;
			}
		} else if (stmt.ref instanceof QualRef) {
			QualRef ref = (QualRef) stmt.ref;
			// TODO: QRef
		} else {
			// Should never go here?
		}
		stmt.val.visit(this, o); // assume all expr store whatever needs to be stored on the stack (or in rax?)
		_asm.add( new Pop(Reg64.RAX) ); // get stmt.vals value
		_asm.add( new Mov_rmr( new R(Reg64.RBP, RBPOffset, Reg64.RAX))); // assign rax (stmt.val) to ref [rbp - offset]
		return null;
	}
	@Override
	public Object visitIxAssignStmt(IxAssignStmt stmt, Object o){
		stmt.ref.visit(this, o);
		stmt.ix.visit(this, o);
		stmt.exp.visit(this, o);
		return null;
	}
	@Override
	public Object visitCallStmt(CallStmt stmt, Object o){
		stmt.methodRef.visit(this, o);
		stmt.methodRef.visit(this, o);
		if (stmt.methodRef.declaration instanceof MethodDecl) {
			for (Expression e: stmt.argList) {
				e.visit(this, o);
			}

		}
		return null;
	}
	@Override
	public Object visitReturnStmt(ReturnStmt stmt, Object o){
		if (stmt.returnExpr != null)
			return stmt.returnExpr.visit(this, o);
		return null;
	}
	@Override
	public Object visitIfStmt(IfStmt stmt, Object o){
		stmt.cond.visit(this, o);
		stmt.thenStmt.visit(this, o);
		if (stmt.elseStmt != null)
			stmt.elseStmt.visit(this, o);
		return null;
	}
	@Override
	public Object visitWhileStmt(WhileStmt stmt, Object o){
		stmt.cond.visit(this, o);
		stmt.body.visit(this, o);
		return null;
	}


	////////////////////////////////////
	/* EXPRESSIONS */ // TODO: Push expressions evaluations onto the stack
	////////////////////////////////////

	@Override
	public Object visitUnaryExpr(UnaryExpr expr, Object o){
		expr.operator.visit(this, o);
		expr.expr.visit(this, o);
		return null;
	}
	@Override
	public Object visitBinaryExpr(BinaryExpr expr, Object o){
		expr.operator.visit(this, o);
		expr.left.visit(this, o);
		expr.right.visit(this, o);
		return null;
	}
	@Override
	public Object visitRefExpr(RefExpr expr, Object o){
		expr.ref.visit(this, o);
    	return null;
	}
	@Override
	public Object visitIxExpr(IxExpr ie, Object o){
		ie.ref.visit(this, o);
		ie.ixExpr.visit(this, o);
		return null;
	}
	@Override
	public Object visitCallExpr(CallExpr expr, Object o) {
		expr.functionRef.visit(this, o);
		return null;
	}
	@Override
	public Object visitLiteralExpr(LiteralExpr expr, Object o){
		expr.lit.visit(this, o);
		return null;
	}
	@Override
	public Object visitNewArrayExpr(NewArrayExpr expr, Object o){
		expr.eltType.visit(this, o);
		expr.sizeExpr.visit(this, o);
		return null;
	}
	@Override
	public Object visitNewObjectExpr(NewObjectExpr expr, Object o){
		expr.classtype.visit(this, o);
		return null;
	}

	////////////////////////////////////
	/* EXPRESSIONS */  // TODO: Push literals onto the stack
	////////////////////////////////////

	@Override
	public Object visitIdentifier(Identifier id, Object o){
		return null;
	}
	@Override
	public Object visitOperator(Operator op, Object o){
		return null;
	}
	@Override
	public Object visitIntLiteral(IntLiteral num, Object o){
		return null;
	}
	@Override
	public Object visitBooleanLiteral(BooleanLiteral bool, Object o){
		return null;
	}
	@Override
	public Object visitNullLiteral(NullLiteral nl, Object o) {
		return null;
	}
}
