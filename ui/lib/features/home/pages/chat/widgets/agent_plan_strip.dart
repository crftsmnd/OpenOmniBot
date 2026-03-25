import 'package:flutter/material.dart';
import 'package:ui/services/assists_core_service.dart';

class AgentPlanStrip extends StatelessWidget {
  const AgentPlanStrip({
    super.key,
    required this.state,
    required this.expanded,
    required this.onToggleExpanded,
  });

  final AgentRunStateData state;
  final bool expanded;
  final VoidCallback onToggleExpanded;

  @override
  Widget build(BuildContext context) {
    final steps = state.steps;
    final completedCount = steps
        .where((item) => item.status == 'completed')
        .length;
    final runningIndex = steps.indexWhere(
      (item) => item.id == state.currentStepId,
    );
    final currentTitle = runningIndex >= 0
        ? steps[runningIndex].title
        : steps
              .firstWhere(
                (item) => item.status == 'running',
                orElse: () => steps.isNotEmpty
                    ? steps.first
                    : const AgentPlanStepData(
                        id: '',
                        title: '准备中',
                        status: 'pending',
                        order: 0,
                      ),
              )
              .title;

    return AnimatedSize(
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutCubic,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: onToggleExpanded,
            child: Container(
              padding: const EdgeInsets.fromLTRB(14, 10, 12, 10),
              decoration: BoxDecoration(
                color: const Color(0xFFF4F8FF),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFD8E7FF)),
              ),
              child: Row(
                children: [
                  _PhaseBadge(phase: state.phase),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          currentTitle.isEmpty ? '准备执行' : currentTitle,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                            color: Color(0xFF1F3350),
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '${runningIndex >= 0 ? runningIndex + 1 : completedCount.clamp(0, steps.length)}/${steps.isEmpty ? 1 : steps.length} · ${_phaseLabel(state.phase)}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 11,
                            color: Color(0xFF667A99),
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Icon(
                    expanded
                        ? Icons.expand_less_rounded
                        : Icons.expand_more_rounded,
                    color: const Color(0xFF5B7398),
                  ),
                ],
              ),
            ),
          ),
          if (expanded) ...[
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
              decoration: BoxDecoration(
                color: const Color(0xFFF9FBFF),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: const Color(0xFFE0EBFB)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Workflow',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF24446F),
                    ),
                  ),
                  const SizedBox(height: 10),
                  _AgentWorkflowGraph(workflow: state.workflow),
                  const SizedBox(height: 12),
                  const Text(
                    'Plan',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF24446F),
                    ),
                  ),
                  const SizedBox(height: 8),
                  ...steps.map(_buildStepTile),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildStepTile(AgentPlanStepData step) {
    final accent = _statusColor(step.status);
    final isActive = step.id == state.currentStepId || step.status == 'running';
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.fromLTRB(10, 9, 10, 9),
      decoration: BoxDecoration(
        color: isActive ? const Color(0xFFEFF5FF) : Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isActive ? const Color(0xFFBFD7FF) : const Color(0xFFE7EEF8),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 20,
            height: 20,
            decoration: BoxDecoration(
              color: accent.withValues(alpha: 0.14),
              shape: BoxShape.circle,
            ),
            alignment: Alignment.center,
            child: Icon(_statusIcon(step.status), size: 12, color: accent),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  step.title,
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF22324C),
                  ),
                ),
                if (step.summary.isNotEmpty) ...[
                  const SizedBox(height: 3),
                  Text(
                    step.summary,
                    style: const TextStyle(
                      fontSize: 11,
                      height: 1.3,
                      color: Color(0xFF637895),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AgentWorkflowGraph extends StatelessWidget {
  const _AgentWorkflowGraph({required this.workflow});

  final AgentWorkflowData workflow;

  @override
  Widget build(BuildContext context) {
    final nodes = [...workflow.nodes]
      ..sort((a, b) => a.order.compareTo(b.order));
    if (nodes.isEmpty) {
      return const SizedBox.shrink();
    }
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          for (var i = 0; i < nodes.length; i++) ...[
            _WorkflowNodeChip(
              node: nodes[i],
              isActive: workflow.activeNodeId == nodes[i].id,
            ),
            if (i != nodes.length - 1)
              Container(
                width: 26,
                height: 2,
                margin: const EdgeInsets.symmetric(horizontal: 4),
                color: _edgeColor(nodes[i].status),
              ),
          ],
        ],
      ),
    );
  }
}

class _WorkflowNodeChip extends StatelessWidget {
  const _WorkflowNodeChip({required this.node, required this.isActive});

  final AgentWorkflowNodeData node;
  final bool isActive;

  @override
  Widget build(BuildContext context) {
    final accent = _statusColor(node.status);
    return Container(
      width: 88,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
      decoration: BoxDecoration(
        color: isActive ? const Color(0xFFEAF3FF) : Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: isActive ? const Color(0xFFBED7FF) : const Color(0xFFE5EDF8),
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 22,
            height: 22,
            decoration: BoxDecoration(
              color: accent.withValues(alpha: 0.14),
              shape: BoxShape.circle,
            ),
            alignment: Alignment.center,
            child: Icon(_statusIcon(node.status), size: 12, color: accent),
          ),
          const SizedBox(height: 6),
          Text(
            node.title,
            maxLines: 2,
            textAlign: TextAlign.center,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 11,
              height: 1.2,
              fontWeight: FontWeight.w700,
              color: Color(0xFF22324C),
            ),
          ),
        ],
      ),
    );
  }
}

class _PhaseBadge extends StatelessWidget {
  const _PhaseBadge({required this.phase});

  final String phase;

  @override
  Widget build(BuildContext context) {
    final color = _statusColor(phase);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        _phaseLabel(phase),
        style: TextStyle(
          fontSize: 10,
          fontWeight: FontWeight.w700,
          color: color,
        ),
      ),
    );
  }
}

Color _statusColor(String status) {
  switch (status) {
    case 'completed':
      return const Color(0xFF198B5B);
    case 'running':
    case 'active':
    case 'planning':
    case 'executing':
    case 'replanning':
    case 'compressing':
    case 'finalizing':
      return const Color(0xFF2E7CF6);
    case 'failed':
      return const Color(0xFFD64545);
    case 'skipped':
      return const Color(0xFF8A94A6);
    default:
      return const Color(0xFF7A879B);
  }
}

Color _edgeColor(String status) {
  switch (status) {
    case 'completed':
      return const Color(0xFF8BC2AE);
    case 'active':
    case 'running':
      return const Color(0xFF8EBBFF);
    case 'failed':
      return const Color(0xFFFFB8B8);
    default:
      return const Color(0xFFD8E3F5);
  }
}

IconData _statusIcon(String status) {
  switch (status) {
    case 'completed':
      return Icons.check_rounded;
    case 'running':
    case 'active':
    case 'planning':
    case 'executing':
    case 'replanning':
    case 'compressing':
    case 'finalizing':
      return Icons.more_horiz_rounded;
    case 'failed':
      return Icons.close_rounded;
    case 'skipped':
      return Icons.skip_next_rounded;
    default:
      return Icons.circle_outlined;
  }
}

String _phaseLabel(String phase) {
  switch (phase) {
    case 'planning':
      return 'Planning';
    case 'executing':
      return 'Executing';
    case 'replanning':
      return 'Replan';
    case 'compressing':
      return 'Compress';
    case 'finalizing':
      return 'Finalizing';
    case 'completed':
      return 'Done';
    case 'failed':
      return 'Failed';
    default:
      return phase;
  }
}
