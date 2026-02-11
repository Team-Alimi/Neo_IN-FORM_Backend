package today.inform.inform_backend.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.common.exception.BusinessException;
import today.inform.inform_backend.common.exception.ErrorCode;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.Vendor;
import today.inform.inform_backend.repository.UserRepository;
import today.inform.inform_backend.repository.VendorRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;

    @Transactional
    public void updateMajor(Integer userId, Integer majorId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Vendor major = vendorRepository.findById(majorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "존재하지 않는 학과입니다."));

        user.updateMajor(major);
    }
}
