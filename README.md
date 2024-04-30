# MiniJava Compiler
This compiler for MiniJava, a Java subset, follows a four-step process: scanning, parsing, identification/type checking, and code generation.
# Syntactic Analysis
All characters and character sets were tokenized during scanning, with no token minimization. Recursive descent was used for parsing.
# AST Generation
Refer to ASTChanges.txt for additional AST data beyond the normalized syntax of the AST.
# Contextual Analysis
The AST was traversed twice. The first traversal involved identification, with declarations added to all identifiers using `ScopedIdentification.java`, which, in turn, used `IDTable.java` for each scoped declaration level. The second traversal involved type checking all assignments and operations.
# Code Generation
Targeted x84/x64 architecture, with an additional AST traversal and no register optimization. Memory layout details:
- Static fields: Offset from a specific point assigned by RBP pre-execution.
- Local variables: Stored at -8 byte offsets from current RBP.
- New objects: Allocated on the heap with fields at +8 byte offsets from the original heap pointer. The base heap pointer was stored on the stack in the same way local variables are stored. 
- Method calls: RBP was updated for each method call. Parameters were pushed to the stack and stored at +8 byte offsets from the new RBP.
# Optimization
Several greedy decisions were made:
- Each new object was allocated an entire heap block.
- All integers were represented with 64 bits.
- Overflow was not managed.
- Register displacement always used maximum bytes allowed.
- Array lengths were unrestricted up until block size limits.

Future work may include improvements, optimizations, additional features, bug fixes, and more.
