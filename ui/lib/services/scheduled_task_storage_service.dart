import 'dart:convert';
import 'package:ui/models/scheduled_task.dart';
import 'package:ui/services/storage_service.dart';

/// 定时任务存储服务
class ScheduledTaskStorageService {
  static const String _scheduledTasksKey = 'scheduled_tasks';

  /// 保存所有定时任务
  static Future<bool> saveScheduledTasks(List<ScheduledTask> tasks) async {
    try {
      final jsonList = tasks.map((task) => jsonEncode(task.toJson())).toList();
      return await StorageService.setStringList(_scheduledTasksKey, jsonList);
    } catch (e) {
      print('保存定时任务失败: $e');
      return false;
    }
  }

  /// 加载所有定时任务
  static Future<List<ScheduledTask>> loadScheduledTasks() async {
    try {
      final jsonList = StorageService.getStringList(_scheduledTasksKey);

      if (jsonList == null || jsonList.isEmpty) {
        return [];
      }

      return jsonList.map((jsonStr) {
        final json = jsonDecode(jsonStr) as Map<String, dynamic>;
        return ScheduledTask.fromJson(json);
      }).toList();
    } catch (e) {
      print('加载定时任务失败: $e');
      return [];
    }
  }

  /// 添加定时任务
  static Future<bool> addScheduledTask(ScheduledTask task) async {
    try {
      final tasks = await loadScheduledTasks();

      // 检查是否已存在相同的任务
      final existingIndex = tasks.indexWhere((t) => t.id == task.id);
      if (existingIndex != -1) {
        tasks[existingIndex] = task;
      } else {
        tasks.add(task);
      }

      return await saveScheduledTasks(tasks);
    } catch (e) {
      print('添加定时任务失败: $e');
      return false;
    }
  }

  /// 更新定时任务
  static Future<bool> updateScheduledTask(ScheduledTask task) async {
    try {
      final tasks = await loadScheduledTasks();
      final index = tasks.indexWhere((t) => t.id == task.id);

      if (index == -1) {
        return false;
      }

      tasks[index] = task;
      return await saveScheduledTasks(tasks);
    } catch (e) {
      print('更新定时任务失败: $e');
      return false;
    }
  }

  /// 删除定时任务
  static Future<bool> deleteScheduledTask(String taskId) async {
    try {
      final tasks = await loadScheduledTasks();
      tasks.removeWhere((task) => task.id == taskId);
      return await saveScheduledTasks(tasks);
    } catch (e) {
      print('删除定时任务失败: $e');
      return false;
    }
  }

  /// 根据ID获取定时任务
  static Future<ScheduledTask?> getScheduledTaskById(String taskId) async {
    try {
      final tasks = await loadScheduledTasks();
      return tasks.firstWhere(
        (task) => task.id == taskId,
        orElse: () => throw Exception('Task not found'),
      );
    } catch (e) {
      return null;
    }
  }

  /// 获取启用的定时任务
  static Future<List<ScheduledTask>> getEnabledScheduledTasks() async {
    final tasks = await loadScheduledTasks();
    return tasks.where((task) => task.isEnabled).toList();
  }

  /// 清空所有定时任务
  static Future<bool> clearScheduledTasks() async {
    try {
      return await StorageService.remove(_scheduledTasksKey);
    } catch (e) {
      print('清空定时任务失败: $e');
      return false;
    }
  }

  /// 根据suggestionId检查任务是否已经添加定时
  static Future<ScheduledTask?> getScheduledTaskBySuggestionId(
    String nodeId,
    String suggestionId,
  ) async {
    try {
      final tasks = await loadScheduledTasks();
      return tasks.firstWhere(
        (task) => task.nodeId == nodeId && task.suggestionId == suggestionId,
        orElse: () => throw Exception('Task not found'),
      );
    } catch (e) {
      return null;
    }
  }
}
