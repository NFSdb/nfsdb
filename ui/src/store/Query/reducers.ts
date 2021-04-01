/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2020 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

import { differenceInMilliseconds } from "date-fns"

import { NotificationShape, NotificationType, QueryAction, QueryAT, QueryStateShape } from "types"

export const initialState: QueryStateShape = {
  notifications: [],
  running: false,
  maxNotificationHeight: 500
}

function calculateHeight(notifications: NotificationShape[]): number {
  if (!notifications) return 0;
  let height = 0;
  notifications.forEach(element => {
    // Estimate element height
    if (element.type === NotificationType.SUCCESS ) {
      height += 145
    } else {
      height += 70;
    }
  });
  return height;
}

const query = (state = initialState, action: QueryAction): QueryStateShape => {
  switch (action.type) {
    case QueryAT.ADD_NOTIFICATION: {
      const notifications = [action.payload, ...state.notifications]

      while (notifications.length > 1 && calculateHeight(notifications) > state.maxNotificationHeight) {
        notifications.pop()
      }

      return {
        ...state,
        notifications,
      }
    }

    case QueryAT.CLEANUP_NOTIFICATIONS: {
      return {
        ...state,
        notifications: [],
      }
    }

    case QueryAT.REMOVE_NOTIFICATION: {
      return {
        ...state,
        notifications: state.notifications.filter(
          ({ createdAt }) => createdAt !== action.payload,
        ),
      }
    }

    case QueryAT.SET_RESULT: {
      return {
        ...state,
        result: action.payload,
      }
    }

    case QueryAT.STOP_RUNNING: {
      return {
        ...state,
        running: false,
      }
    }

    case QueryAT.TOGGLE_RUNNING: {
      return {
        ...state,
        running: !state.running,
      }
    }

    case QueryAT.CHANGE_MAX_NOTIFICATION_HEIGHTS: {
      let notifications = state.notifications

      while (calculateHeight(notifications) > action.payload) {
        if (notifications == state.notifications) {
          // copy
          notifications = [...state.notifications]
        }
        notifications.pop()
      }

      return {
        ...state, 
        notifications,
        maxNotificationHeight: action.payload,
      }
    }

    default:
      return state
  }
}

export default query
