package miniJava.CodeGeneration;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGeneration.x64.*;
import miniJava.CodeGeneration.x64.ISA.*;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.swing.*;
import java.lang.reflect.Method;
import java.nio.ReadOnlyBufferException;
import java.util.RandomAccess;
import java.util.spi.ResourceBundleProvider;

public class CodeGenerator implements Visitor<Object, Object> {
	private ErrorReporter _errors;
	private InstructionList _asm; // our list of instructions that are used to make the code section
	private int _mainAddress = -1;
	
	public CodeGenerator(ErrorReporter errors) {
		this._errors = errors;
	}
	
	public void parse(Package prog) {
		_asm = new InstructionList();
		_asm.markOutputStart();
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
		int staticFieldCount = 0;
		int fieldCount = 0;
		for (ClassDecl c : prog.classDeclList) {
			for (FieldDecl f: c.fieldDeclList) {
				if (f.isStatic) {
					staticFieldCount++;
					_asm.add( new Push(0) ); // update rsp
					f.offset = staticFieldCount * -8; // update relative offset (from r15 because they're fields ya know)
				} else {
					fieldCount++;
//					_asm.add( new Push(0) ); // update rsp
					f.offset = fieldCount * 8;
				}
//				f.visit(this, c);
//			_asm.add(new Push(0));
//			makeMalloc(); // store resulting ptr in RAX
//			_asm.add( new Mov_rmr(new R(Reg64.RBP, -8, Reg64.RAX)));
//			f.visit(this, clas);
			}
			fieldCount = 0;
		}

		prog.visit(this,null);

    // Output the file "a.out" if no errors
		if (!_errors.hasErrors()) {
      		makeElf("a.out");
		}
		_asm.outputFromMark();

	}

	@Override
	public Object visitPackage(Package prog, Object arg) {
		/*
				int heapPtr = makeMalloc();

		- r15 points to very base of stack (rbp) where pointers to fields are stored
		-
		 */
		_asm.add( new Mov_rmr( new R(Reg64.R15, Reg64.RBP) ) ); // move stack pointer at start of program into r15

		for (ClassDecl c: prog.classDeclList){
			c.visit(this, c);
		}
		if (_mainAddress == -1) { // if there is no main method
			_errors.reportError("CodeGenerationError: Program must have exactly one main method");
		}
		return null;
	}

	public void makeElf(String fname) {
		ELFMaker elf = new ELFMaker(_errors, _asm.getSize(), 8); // bss ignored until PA5, set to 8
		elf.outputELF(fname, _asm.getBytes(), _mainAddress);
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
		int idxStart = _asm.add( new Mov_rmi( new R(Reg64.RAX,true),1) );

		_asm.add( new Mov_rmi( new R(Reg64.RDI,true),1) ); // fd = 1 (stdout)
		_asm.add( new Mov_rmr( new R(Reg64.RSI, Reg64.RSP) ) ); // rax = address of string
		_asm.add( new Mov_rmi( new R(Reg64.RDX,true),1) ); // length of str = 1
		_asm.add( new Syscall() );
		_asm.add( new Pop( Reg64.RAX) );

		return idxStart;
	}

	////////////////////////////////////
	/* AST CODE GENERATION TRAVERSAL */
	////////////////////////////////////

	////////////////////////////////////
	/* DECLARATIONS */
	////////////////////////////////////

	@Override
	public Object visitClassDecl(ClassDecl clas, Object o){
		/*

		---------------Don't Need This because we are not actually creating the objects?---------------
		- clas has it's own pointer  (basePtr) that points to where the start of all instance vars are located
		- fieldCount = a counter that tracks the number of fields visited
		- staticFieldCount = a counter that tracks the number of static fields visiited
		- for each non-static field:
			-> fieldCount++
			-> field.offset = fieldCount * 8
			-> visit field and store value in rax
			-> mov [basePtr + field.offset], rax
		---------------------------------------------------------------------------------------------
		- for each static field:
			-> staticFieldCount++
			-> field.offset = staticFieldCount * 8
			-> visit field and store value in rax
			-> mov [basePtr + field.offset, rax]
		 */

		for (MethodDecl m: clas.methodDeclList) {
			if (m.name.equals("main") && m.isStatic && !m.isPrivate && m.type.typeKind == TypeKind.VOID && m.parameterDeclList.size() == 1 && m.parameterDeclList.get(0).name.equals("args") && m.parameterDeclList.get(0).type.typeKind == TypeKind.ARRAY && ((ArrayType)m.parameterDeclList.get(0).type).eltType.typeKind == TypeKind.CLASS && ((ClassType)((ArrayType)m.parameterDeclList.get(0).type).eltType).className.getName().equals("String")) {
				if (_mainAddress != -1) { // if main already exists
					_errors.reportError("CodeGenerationError: Program cannot have multiple main methods");
				} else {
					_mainAddress = _asm.getSize();
				}
				m.visit(this, o);
			} else {
				m.visit(this, o);
			}
		}
		return null;
	}

	@Override
	public Object visitFieldDecl(FieldDecl f, Object o){
		f.type.visit(this, o);
		return null;
	}

	@Override
	public Object visitMethodDecl(MethodDecl m, Object o){
		m.address = _asm.getSize();

		// patch calls that were made before method was visited
		for (Instruction i : m.instrList) {
			_asm.patch(i.listIdx, new Call(i.startAddress, m.address));
		}
		// localVar counter to assign offset
		// the nth localVar will have an offset of n * -8 which will be what needs to be added to rbp to access it
		int varCount = 0;


		int thisOnStack = 0;
    	if (!m.isStatic) // if non-static method, 'this' is on stack
			thisOnStack = 1;

		_asm.add( new Push( Reg64.RBP ) ); // push old rbp onto stack
		_asm.add( new Mov_rmr( new R(Reg64.RBP, Reg64.RSP) ) ); // update rbp to point to wherever rsp is pointing to

		for (int i = m.parameterDeclList.size() - 1; i >= 0; i--) { // loop through the list and give params their offset
			m.parameterDeclList.get(i).offset = (i + 1 + thisOnStack) * 8; // ith param is offset from rbp by i + ra + 'this' (if static) spots
		}

		for (Statement s : m.statementList) {
			if (s instanceof VarDeclStmt) {
				_asm.add( new Push(0) ); // allocate room on stack before storing local var in it

				varCount++;
				((VarDeclStmt) s).varDecl.offset = varCount * -8; // each varDecl will have an offset from rbp
			}
			s.visit(this, o);
		}

		// update rsp to pnt to rbp (top of call stack, where old rbp was stored)
		_asm.add( new Mov_rmr( new R(Reg64.RSP, Reg64.RBP) ) );
		_asm.add( new Pop( Reg64.RBP ) );	// restore old rbp val

		if (m.address == _mainAddress) {
			_asm.add( new Mov_ri64(Reg64.RAX, 60) );
			_asm.add( new Xor( new R(Reg64.RDI, Reg64.RDI) ) );
			_asm.add( new Syscall() );
		}

		_asm.add( new Ret( (short)(m.parameterDeclList.size() + thisOnStack) ) ); // return from method


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
//		stmt.varDecl.visit(this, o);
		if (stmt.initExp != null) {
			stmt.initExp.visit(this, o);
			_asm.add( new Pop(Reg64.RAX) );
//			_asm.add( new Pop(Reg64.RAX) ); // get stmt.initExp result from stack
//			_asm.add( new Push(0) ); // all this does is basically update the rsp
			_asm.add( new Mov_rmr( new R(Reg64.RBP, stmt.varDecl.offset, Reg64.RAX) ) ); // move stmt.initExp into local var [rbp - offset]
		}
		return null;
	}
	@Override
	public Object visitAssignStmt(AssignStmt stmt, Object o){
		/*----------------------------------
		let var being assigned to = 'var'
		Possibilities:
			- var is a local var
				-> accessed by IdRef
				-> varDecl = VarDecl
			- var is an instance var
				-> accessed by QRef
				-> varDecl = MemberDecl?
			- var is a static var
				-> accessed by IdRef
				-> varDecl = FieldDecl
		----------------------------------*/
		int offset = 0;
//		boolean isStatic = false;
		stmt.ref.visit(this, true); // true if we want the ref to return an address
		stmt.val.visit(this, o); // will return the value of the expr being visited

		_asm.add( new Pop(Reg64.RBX) ); // rbx = val
		_asm.add( new Pop(Reg64.RDI)); // destination address stored in rdi

		if (stmt.ref instanceof IdRef) {
			IdRef ref = (IdRef) stmt.ref;
			if (ref.declaration instanceof VarDecl) { // if assigning to local var
				offset = ((VarDecl) ref.declaration).offset;
				_asm.add( new Mov_rmr( new R(Reg64.RBP, offset, Reg64.RBX))); // assign rax (stmt.val) to ref [rbp - offset]
				return null;
			} else if (ref.declaration instanceof FieldDecl) { // if assigning to static var
				offset = ((FieldDecl) ref.declaration).offset;
				_asm.add( new Mov_rmr( new R(Reg64.R15, offset, Reg64.RBX))); // mov rax into [r15 + offset]
				return null;
			}
		} else if (stmt.ref instanceof QualRef) {	// if assigning to instance vars
			QualRef ref = (QualRef) stmt.ref;
			Declaration decl = ref.id.getDeclaration();
			_asm.add( new Mov_rmr( new R(Reg64.RDI, Reg64.RBX) ) ); // offset accounted for already in visitQRef?
//			_asm.add( new Mov_rmr( new R(Reg64.RDI, ((FieldDecl)decl).offset, Reg64.RBX) ) );
			return null;
		} else {
			// Should never go here?
		}

		return null;
	}
	@Override
	public Object visitIxAssignStmt(IxAssignStmt stmt, Object o){
		stmt.ref.visit(this, true); // = address of array // Mark: Might need to change argument to true or false?
//		_asm.add( new Mov_rrm( new R(Reg64.RAX, Reg64.RBX))); // rbx = array address
		stmt.ix.visit(this, o);
//		_asm.add( new Mov_rrm( new R(Reg64.RAX, Reg64.RCX) ) ); //rcx = index
		stmt.exp.visit(this, o);

		_asm.add( new Pop(Reg64.RAX) ); // rax = expr
		_asm.add( new Pop(Reg64.RCX) ); // rcx = index
		_asm.add( new Pop(Reg64.RBX) ); // rbx = address of array


		// load address of array[index] -> lea rdx, [rbx + rcx*8]
		_asm.add( new Lea( new R(Reg64.RBX, Reg64.RCX, 8, 0, Reg64.RDX) ) );
		// store expr (rax) at that address -> mov [rdx] rax
		_asm.add( new Mov_rmr( new R(Reg64.RDX, Reg64.RAX) ) );
		return null;
	}
	@Override
	public Object visitCallStmt(CallStmt stmt, Object o){

		// Check for println call stmt
		if (stmt.methodRef instanceof QualRef && ((QualRef) stmt.methodRef).id.spelling.equals("println")) {
			_asm.outputFromMark();
			_asm.markOutputStart();
			stmt.argList.get(0).visit(this, 0);
			makePrintln();
			return null;
		}

		// store params in reverse order on stack
		for (int i = stmt.argList.size() - 1; i >= 0; i--) {
			stmt.argList.get(i).visit(this, o); // result stored on stack already?
//			_asm.add( new Push(Reg64.RAX) );	// push param to the stack in reverse order
		}

		MethodDecl md;
		// Push 'this' if QRef
		if (stmt.methodRef instanceof QualRef) {
			((QualRef) stmt.methodRef).ref.visit(this, true); // visit lhs of ref to get the address of the vars location on the heap (stored in rax)
//			_asm.add( new Pop( Reg64.RDI) ); // RDI = address of method in the lhs context
			md = (MethodDecl) ((QualRef) stmt.methodRef).id.getDeclaration();
		} else { // ref is an IdRef?
//			_asm.add( new Lea( new R(Reg64.RBP, Reg64.RDI) ) ); // RDI = RBP = address of method in local context
			_asm.add( new Push( new R(Reg64.RBP, 16)) );
			md = (MethodDecl) ((IdRef) stmt.methodRef).id.getDeclaration();
		}

		if (md.address == -1) { // if method has not yet been visited
			Instruction callInstr = new Call(0);
			_asm.add( callInstr ); // add empty call statement instruction
			md.instrList.add(callInstr);
		} else { // method has been visited and we know the address
			_asm.add( new Call(_asm.getSize(), md.address) );
		}
		return null;
	}
	@Override
	public Object visitReturnStmt(ReturnStmt stmt, Object o){
		if (stmt.returnExpr != null) {
			stmt.returnExpr.visit(this, o);
			_asm.add( new Pop(Reg64.RAX) );
		} else {
			// rax == null;??
		}


//		// code for leaving function -> mov rsp rbp -> pop rbp -> ret
//		_asm.add( new Mov_rmr(new R(Reg64.RSP, Reg64.RBP) ) );
//		_asm.add( new Pop(Reg64.RBP) );
//		_asm.add( new Ret() );

		return null;
	}

	@Override
	public Object visitIfStmt(IfStmt stmt, Object o){
    // should evaluate to be a literalExpression at heart (true = 1) (false = 0);
    System.out.println("START IFSTMT");
	_asm.markOutputStart();
		stmt.cond.visit(this, o); // true or false result stored on stack
		_asm.add( new Pop(Reg64.RAX) );

		// evaluate condition
		_asm.add( new Cmp( new R(Reg64.RAX, true), 0) );


		Instruction jmp = new CondJmp(Condition.E, 0); // 32-bit offset conditional jump to nowhere if cond is false
		_asm.add( jmp );

		stmt.thenStmt.visit(this, o);

		Instruction jmpPastElse = new Jmp(0); // if condition == true jump past else
		_asm.add( jmpPastElse );

		_asm.patch( jmp.listIdx, new CondJmp(Condition.E, jmp.startAddress, _asm.getSize(), false));
		if (stmt.elseStmt != null) {
			stmt.elseStmt.visit(this, o);
			_asm.patch(jmpPastElse.listIdx, new Jmp(jmpPastElse.startAddress, _asm.getSize(), false));
		}
		_asm.outputFromMark();
    System.out.println("END IFSTMT");
		return null;
	}
	@Override
	public Object visitWhileStmt(WhileStmt stmt, Object o){
    System.out.println("START:   WHILE LOOP");
	_asm.markOutputStart();
		int beforeLoopIdx = _asm.getSize(); // to jump back to

		stmt.cond.visit(this, o); // true (1) or false (0) stored on stack
		_asm.add( new Pop(Reg64.RAX) );
		_asm.add( new Cmp( new R(Reg64.RAX, true), 0) );
		Instruction condJmp = new CondJmp(Condition.E, 0); // jump to end of while loop if the condition is false

		stmt.body.visit(this, o);
		_asm.add( new Jmp(_asm.getSize(), beforeLoopIdx, false) ); // jump to beginning of whileloop

		_asm.patch( condJmp.listIdx, new CondJmp(Condition.E, condJmp.startAddress, _asm.getSize(), false)); // while loop jumps to after body when condition is not met
		_asm.outputFromMark();
		System.out.println("END:   WHILE LOOP");
		return null;
	}


	////////////////////////////////////
	/* EXPRESSIONS */
	/* ALL EXPRESSION RETURN VALUES STORED IN RAX */
	/* RAX isRM -> IxExpr, RefExpr, CallExpr (potentially), Expression, NewObjectExpr, NewArrayExpr */
	////////////////////////////////////

	@Override
	public Object visitUnaryExpr(UnaryExpr expr, Object o){
		Operator op = expr.operator;
		expr.expr.visit(this, o);
		_asm.add( new Pop(Reg64.RAX) );

		if (op.spelling.equals("!")) {
			_asm.add( new Not( new R(Reg64.RAX, false) ) );
		} else {
			_asm.add( new Neg( new R(Reg64.RAX, false) ) );
		}
		_asm.add( new Push(Reg64.RAX) );
		return null;
	}
	@Override
	public Object visitBinaryExpr(BinaryExpr expr, Object o){
//    System.out.println("START BINEXPR");
		expr.operator.visit(this, o);
		expr.left.visit(this, o);
		expr.right.visit(this, o); // rhs of binop in RAX
		_asm.add( new Pop(Reg64.RBX) ); // rhs in RBX
		_asm.add( new Pop(Reg64.RAX) ); // lhs in RAX



		Condition cond = Condition.getCond(expr.operator);
		if (cond != null) { // if operator does a comparison
			_asm.add( new Xor( new R(Reg64.RDX, Reg64.RDX) ) );
			_asm.add( new Cmp( new R(Reg64.RAX, Reg64.RBX) ) );
			_asm.add( new SetCond( cond, Reg8.DL) );
			_asm.add( new Mov_rmr( new R(Reg64.RAX, Reg8.DL) ) );
			_asm.add( new Push(Reg64.RAX) );
			return null;
		}

		switch (expr.operator.spelling) {
			case "+":
				_asm.add( new Add( new R(Reg64.RAX, Reg64.RBX) ) );
				break;
			case "-":
				_asm.add( new Sub( new R(Reg64.RAX, Reg64.RBX) ) );
				break;
			case "||":
				_asm.add( new Or( new R(Reg64.RAX, Reg64.RBX) ) );
				break;
			case "&&":
				_asm.add( new And( new R(Reg64.RAX, Reg64.RBX) ) );
				break;
//			case "*" -> _asm.add( new Imul( new R(Reg64.RAX, Reg64.RBX) ) );
			case "*":
				_asm.add( new Imul( new R(Reg64.RBX, true) ) );
				break;
//			case "/" -> _asm.add( new Idiv( new R(Reg64.RAX, Reg64.RBX) ) );
			case "/":
				_asm.add( new Idiv( new R(Reg64.RBX, true) ) );
				break;
		}
		_asm.add( new Push(Reg64.RAX) );
		return null;
	}
	@Override
	public Object visitRefExpr(RefExpr expr, Object o){
		expr.ref.visit(this, o);
		// Potentially don't have to do anything here as ref.visit will store return value in rax for us
//		Reference ref = expr.ref;
//		if (ref instanceof IdRef) {
//
//		} else if (ref instanceof QualRef) {
//
//		} else {
//
//		}
    	return null;
	}
	@Override
	public Object visitIxExpr(IxExpr ie, Object o){
		ie.ref.visit(this, o);
//		_asm.add( new Pop(Reg64.RBX) );
//		_asm.add( new Mov_rrm( new R(Reg64.RAX, Reg64.RBX) ) ); // moves RAX (rm) into RBX (r)? (aka saves arrady address to rbx)
		ie.ixExpr.visit(this, o);
//		_asm.add( new Mov_rmr(new R(Reg64.RAX, Reg64.RCX) ) );
		_asm.add( new Pop(Reg64.RCX) );
		_asm.add( new Pop(Reg64.RBX) );

		// load address of array[index] -> lea rdx, [rbx + rcx*8]
		_asm.add( new Lea( new R(Reg64.RBX, Reg64.RCX, 8, 0, Reg64.RDX) ) );
		// store expr (rax) at that address -> mov rax, [rdx]
		_asm.add( new Mov_rrm( new R(Reg64.RDX, Reg64.RAX) ) );
		_asm.add( new Push(Reg64.RAX) );
		return null;
	}
	@Override
	public Object visitCallExpr(CallExpr expr, Object o) {
		// This is where methods get called ( I say just visit the method lwk)
		/*
		if we know method Decl address cmp to method decl address
		if not do Call(0) and add the incomplete instruction to a list contatined in methodDecl
		when finally visiting method decl loop through the list and patch all instructions
		 */
		// store params in reverse order on stack
//		expr.functionRef.visit(this, o);
		for (int i = expr.argList.size() - 1; i >= 0; i--) {
			expr.argList.get(i).visit(this, o); // result stored in rax
			_asm.add( new Push(Reg64.RAX) );	// push param to the stack in reverse order
		}

		MethodDecl md;
		// Push 'this' if QRef
		if (expr.functionRef instanceof QualRef) {
			((QualRef) expr.functionRef).ref.visit(this, true); // visit lhs of ref to get the address of the vars location on the heap (stored in rax)
//			_asm.add( new Pop( Reg64.RDI) ); // RDI = address of method in the lhs context
			md = (MethodDecl) ((QualRef) expr.functionRef).id.getDeclaration();
		} else { // ref is an IdRef?
//			_asm.add( new Lea( new R(Reg64.RBP, Reg64.RDI) ) ); // RDI = RBP = address of method in local context
			_asm.add( new Push( new R(Reg64.RBP, 16)) );
			md = (MethodDecl) ((IdRef) expr.functionRef).id.getDeclaration();
		}

		if (md.address == -1) { // if method has not yet been visited
			Instruction callInstr = new Call(0);
			_asm.add( callInstr ); // add empty call statement instruction
			md.instrList.add(callInstr);
		} else { // method has been visited and we know the address
			_asm.add( new Call(_asm.getSize(), md.address) );
		}
		_asm.add( new Push(Reg64.RAX) );
		return null;
	}
	@Override
	public Object visitLiteralExpr(LiteralExpr expr, Object o){
		expr.lit.visit(this, o);
		_asm.add( new Push(Reg64.RAX) );
		return null;
	}
	@Override
	public Object visitNewArrayExpr(NewArrayExpr expr, Object o){
		expr.eltType.visit(this, o);
		expr.sizeExpr.visit(this, o); // size of array stored in rax
//		_asm.add( new Push(Reg64.RAX) ); // save size (rax) before mallocing?
		makeMalloc(); // store ptr to newArray in rax
		_asm.add( new Push(Reg64.RAX) );
		return null;
	}
	@Override
	public Object visitNewObjectExpr(NewObjectExpr expr, Object o){
		/*
		Allocate heap space and store ptr to this space in base ptr
		- clas has its own pointer (basePtr) that points to where the start of all instance vars are located
		- fieldCount = a counter that tracks the number of fields visited
		- staticFieldCount = a counter that tracks the number of static fields visiited
		- for each non-static field:
			-> fieldCount++
			-> field.offset = fieldCount * 8
			-> visit field and store value in rax
			-> mov [basePtr + field.offset], rax

		 */

//		expr.classtype.visit(this, o);
//		int field = 0;
//		ClassDecl newO = visitClassType(expr.classtype, o);

//		_asm.add( new Push(0) );
		makeMalloc(); // pointer to newly allocated memory is in rax
//		_asm.add( new Mov_rmr( new R(Reg64.RBP, -8, Reg64.RAX) ) );
//		for (FieldDecl f : newO.fieldDeclList) {
//			if (!f.isStatic) {
//				field++;
//				f.offset = field * 8;
//			}
//		}
      // when returned from this instruction the new object being created will be represented by a
      // ptr in RAX
		_asm.add( new Push(Reg64.RAX) );
		return null;
	}

	////////////////////////////////////
	/* REFERENCES */
	////////////////////////////////////
	@Override
	public Object visitThisRef(ThisRef ref, Object o) { // returns address
		// assume "this" is always in rdi?
//		_asm.add( new Mov_rrm( new R(Reg64.RDI, Reg64.RAX) ) );
		_asm.add( new Push( new R(Reg64.RBP, 16) ) );
		return null;

	}
	@Override
	public Object visitIdRef(IdRef ref, Object o) { // returns address // needs bool arg for address
//		ref.id.visit(this, o);
		if (ref.declaration instanceof FieldDecl) {
			FieldDecl fd = (FieldDecl) ref.declaration;
			if (fd.isStatic) {	// var is a static field -> mov rax, [r15 + offset]
				if (o instanceof Boolean) { // we want address
					_asm.add( new Lea( new R(Reg64.R15, fd.offset, Reg64.RAX) ) );

				} else {
					_asm.add( new Mov_rrm( new R(Reg64.R15, fd.offset, Reg64.RAX) ) );
				}
			} else {	// var is a non-static field
				/* "this" located at rbp + 16
				mov rax, [rbp + 16]			// rax = address of current class
				lea rax, [rax + offset]		// rax = address of current class plus offset of the field in that class to get address of that field
				 */
				_asm.add( new Mov_rrm( new R(Reg64.RBP, 16, Reg64.RDI) ) ); // mov rdi, [rbp + 16]
				_asm.add( new Lea( new R(Reg64.RDI, fd.offset, Reg64.RAX) ) ); // lea rax, [rdi + fd.offset]
				if (!(o instanceof Boolean)) { // we do not want address
					_asm.add( new Mov_rrm( new R(Reg64.RAX, Reg64.RAX) ) ); // mov rax, [rax]
				}
			}
		} else if (ref.declaration instanceof VarDecl) { // var is local -> mov rax, [rbp + offset]
			VarDecl vd = (VarDecl) ref.declaration;
			if (o instanceof Boolean) { // we want the address
				_asm.add( new Lea( new R(Reg64.RBP, vd.offset, Reg64.RAX) ) ); // lea rax, [rbp + vd.offset]
			} else {
				_asm.add( new Mov_rrm( new R(Reg64.RBP, vd.offset, Reg64.RAX) ) ); // mov rax, [rbp + vd.offset]
			}
		}
		_asm.add( new Push(Reg64.RAX) );
		return null;
	}
	@Override
	public Object visitQRef(QualRef qr, Object o) { // returns address
		// pass argument into qualRef to read memory or get address
		qr.ref.visit(this, null);
		_asm.add( new Pop(Reg64.RBX) ); // starting heap address in rbx
//		_asm.add( new Mov_rrm( new R(Reg64.RAX, Reg64.RBX) ) ); // store object's address in rbx

//		qr.id.visit(this, o);
		FieldDecl fd = (FieldDecl) qr.id.getDeclaration();

		if (o instanceof Boolean) { // return memory
      		_asm.add( new Lea(new R(Reg64.RBX, fd.offset, Reg64.RDI))); // lea rdi, [rbx + fd.offset]
		} else {
			_asm.add( new Mov_rrm( new R(Reg64.RBX, fd.offset, Reg64.RDI) ) );
		}
		_asm.add( new Push(Reg64.RDI) );
		return null;
	}

	////////////////////////////////////
	/* TYPES */
	////////////////////////////////////
	@Override
	public Object visitBaseType(BaseType type, Object o){
		return null;
	}
	@Override
	public ClassDecl visitClassType(ClassType ct, Object o){
		ct.className.visit(this, o);
		return (ClassDecl) ct.className.getDeclaration();
	}
	@Override
	public Object visitArrayType(ArrayType type, Object o){
		type.eltType.visit(this, o);
		return null;
	}

	////////////////////////////////////
	/* TERMINALS */
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
		_asm.add( new Mov_ri64( Reg64.RAX, Integer.parseInt(num.spelling) ) );
		return null;
	}
	@Override
	public Object visitBooleanLiteral(BooleanLiteral bool, Object o){
		int boolToNum;
		if (bool.spelling.equals("true")) {
			boolToNum = 1;
		} else {
			boolToNum = 0;
		}
		_asm.add( new Mov_ri64(Reg64.RAX, boolToNum) );
		return null;
	}
	@Override
	public Object visitNullLiteral(NullLiteral nl, Object o) {
		return null;
	}


}
