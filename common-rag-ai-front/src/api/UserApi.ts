import service from "@/http";
type Res = any;
export const UserApi = {
  QueryPage: "/user/page",
  Download: "/user/download",
  Register: "/user/register",
  Update: "/user/update",
  Login: "/user/login",
  GetById: "/user",
  UpdatePassword: "/user/updatePassword"
};

interface RegisterUserDto {
  name: string;
  userName: string;
  password: string;
  phone: string;
  sex: string;
  /** 状态 0：禁用 1：启用，注册时默认启用 */
  status?: number;
}


// 更新用户信息接口
interface UpdateUserDto {
  id: number;
  name: string;
  userName: string;
  phone: string;
  sex: string;
  /** 身份证号仅后台展示，不再由页面提交 */
  idNumber?: string;
}

// 用户列表查询参数：page 从 1 开始，与后端 PageHelper 一致
export interface UserPageQuery {
  page: number;
  pageSize: number;
  /** 姓名模糊搜索，空串表示不限制 */
  name?: string;
}

// 修改密码请求参数接口
export interface UpdatePasswordDto {
  id: number;
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export const registerUserApi = async (data: RegisterUserDto): Promise<Res> => {
  return service.post(UserApi.Register, data);
};

// 登录：后端使用 @RequestParam 接收用户名与密码
export const loginApi = async (userName: string, password: string): Promise<Res> => {
  return service.post(UserApi.Login, null, { params: { userName, password } });
};

// 按主键查询用户信息：个人中心与 Header 用户区共用
export const getUserByIdApi = async (id: number | string): Promise<Res> => {
  return service.get(`${UserApi.GetById}/${id}`);
};

// 用户管理分页查询
export const queryUserPageApi = async (params: UserPageQuery): Promise<Res> => {
  return service.get(UserApi.QueryPage, { params });
};

export const updateUserApi = async (data: UpdateUserDto): Promise<Res> => {
  return service.put(UserApi.Update, data);
};

// 修改密码
export const updatePasswordApi = async (data: UpdatePasswordDto) => {
  return service.post(UserApi.UpdatePassword, data);
};