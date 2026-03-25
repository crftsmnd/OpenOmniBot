import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ui/features/home/pages/chat/widgets/agent_plan_strip.dart';
import 'package:ui/services/assists_core_service.dart';

void main() {
  testWidgets('expands to show workflow graph and plan steps', (tester) async {
    var expanded = false;

    await tester.pumpWidget(
      MaterialApp(
        home: StatefulBuilder(
          builder: (context, setState) {
            return Scaffold(
              body: AgentPlanStrip(
                state: const AgentRunStateData(
                  taskId: 'task-1',
                  runId: 'task-1',
                  phase: 'executing',
                  currentStepId: 'step-2',
                  steps: <AgentPlanStepData>[
                    AgentPlanStepData(
                      id: 'step-1',
                      title: '理解目标',
                      status: 'completed',
                      order: 0,
                    ),
                    AgentPlanStepData(
                      id: 'step-2',
                      title: '执行工具',
                      status: 'running',
                      order: 1,
                      summary: '正在处理中',
                    ),
                    AgentPlanStepData(
                      id: 'step-3',
                      title: '整理结果',
                      status: 'pending',
                      order: 2,
                    ),
                  ],
                  workflow: AgentWorkflowData(
                    nodes: <AgentWorkflowNodeData>[
                      AgentWorkflowNodeData(
                        id: 'prepareInput',
                        title: 'Prepare Input',
                        status: 'completed',
                        order: 0,
                      ),
                      AgentWorkflowNodeData(
                        id: 'executeStep',
                        title: 'Execute Step',
                        status: 'active',
                        order: 1,
                      ),
                    ],
                    edges: <AgentWorkflowEdgeData>[
                      AgentWorkflowEdgeData(
                        from: 'prepareInput',
                        to: 'executeStep',
                      ),
                    ],
                    activeNodeId: 'executeStep',
                  ),
                  contextUsage: AgentContextUsageData(
                    usedTokens: 92000,
                    contextWindow: 128000,
                    utilization: 0.71875,
                  ),
                ),
                expanded: expanded,
                onToggleExpanded: () {
                  setState(() {
                    expanded = !expanded;
                  });
                },
              ),
            );
          },
        ),
      ),
    );

    expect(find.text('执行工具'), findsOneWidget);
    expect(find.text('2/3 · Executing'), findsOneWidget);
    expect(find.text('Workflow'), findsNothing);

    await tester.tap(find.byType(InkWell));
    await tester.pumpAndSettle();

    expect(find.text('Workflow'), findsOneWidget);
    expect(find.text('Plan'), findsOneWidget);
    expect(find.text('Prepare Input'), findsOneWidget);
    expect(find.text('Execute Step'), findsOneWidget);
    expect(find.text('整理结果'), findsOneWidget);
    expect(find.text('正在处理中'), findsOneWidget);
  });
}
